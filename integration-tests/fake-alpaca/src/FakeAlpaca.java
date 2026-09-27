import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Loopback-only Alpaca substitute for the backend integration suite. */
public final class FakeAlpaca {
  static final List<String> requests = new CopyOnWriteArrayList<>(), subscriptions = new CopyOnWriteArrayList<>();
  static final Map<String,List<Socket>> sockets = new ConcurrentHashMap<>();
  static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  static KeyPair pair;

  public static void main(String[] ignored) throws Exception {
    pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    for (String stream : List.of("news", "market", "trade")) sockets.put(stream, new CopyOnWriteArrayList<>());
    HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 18080), 0);
    http.createContext("/health", e -> reply(e, 200, "{\"status\":\"UP\"}"));
    http.createContext("/issuer/.well-known/openid-configuration", e -> reply(e, 200, "{\"issuer\":\"http://127.0.0.1:18080/issuer\",\"jwks_uri\":\"http://127.0.0.1:18080/jwks\"}"));
    http.createContext("/jwks", e -> reply(e, 200, jwks()));
    http.createContext("/control/token", e -> reply(e, 200, json("token", jwt(param(e.getRequestURI().getQuery(), "role")))));
    http.createContext("/control/reset", e -> { requests.clear(); subscriptions.clear(); reply(e, 200, "{}"); });
    http.createContext("/control/requests", e -> reply(e, 200, "[" + String.join(",", requests) + "]"));
    http.createContext("/control/subscriptions", e -> reply(e, 200, "[" + String.join(",", subscriptions) + "]"));
    http.createContext("/control/send", e -> {
      String target = param(e.getRequestURI().getQuery(), "target");
      String payload = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      broadcast(target, payload); reply(e, 200, "{}");
    });
    http.createContext("/realms/trading/protocol/openid-connect/token", e -> {
      String form = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      String client = formValue(form, "client_id");
      String role = switch (client) {
        case "strategy-service" -> "STRATEGY_SERVICE";
        case "account-service" -> "ACCOUNT_SERVICE";
        default -> "ORDER_EXECUTION_SERVICE";
      };
      reply(e, 200, "{\"access_token\":\"" + jwt(role) + "\",\"expires_in\":3600}");
    });
    http.createContext("/v2/account", e -> { record(e); reply(e, 200, account()); });
    http.createContext("/v2/positions", e -> { record(e); reply(e, 200, "[{\"symbol\":\"AAPL\",\"qty\":\"3\",\"avg_entry_price\":\"175.25\",\"market_value\":\"540\",\"cost_basis\":\"525.75\",\"unrealized_pl\":\"14.25\",\"unrealized_plpc\":\"0.027\",\"current_price\":\"180\",\"lastday_price\":\"179\",\"change_today\":\"0.005\"}]"); });
    http.createContext("/v2/watchlists", e -> { record(e); if ("GET".equals(e.getRequestMethod())) reply(e, 200, "[]"); else reply(e, 200, "{\"id\":\"22222222-2222-2222-2222-222222222222\",\"name\":\"IT\",\"assets\":[]}"); });
    http.createContext("/v2/orders", e -> {
      record(e);
      if ("GET".equals(e.getRequestMethod())) { reply(e, 200, "[]"); return; }
      reply(e, 200, "{\"id\":\"" + ORDER_ID + "\",\"client_order_id\":\"fake-client-order\",\"symbol\":\"AAPL\",\"side\":\"buy\",\"type\":\"limit\",\"time_in_force\":\"day\",\"status\":\"new\",\"qty\":\"2\",\"filled_qty\":\"0\",\"created_at\":\"2026-08-22T00:00:00Z\",\"updated_at\":\"2026-08-22T00:00:00Z\"}");
    });
    http.setExecutor(Executors.newCachedThreadPool()); http.start();
    new Thread(() -> websocket(18081, "news"), "fake-news").start();
    new Thread(() -> websocket(18082, "market"), "fake-market").start();
    new Thread(() -> websocket(18083, "trade"), "fake-trade").start();
  }

  static void websocket(int port, String stream) {
    try (ServerSocket server = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))) {
      while (true) {
        Socket socket = server.accept(); handshake(socket); sockets.get(stream).add(socket);
        new Thread(() -> observe(stream, socket), "fake-" + stream + "-client").start();
      }
    } catch (Exception e) { e.printStackTrace(); }
  }

  static void handshake(Socket socket) throws Exception {
    Map<String,String> headers = headers(socket.getInputStream()); String key = headers.get("sec-websocket-key");
    String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
        .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
    OutputStream output = socket.getOutputStream();
    output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); output.flush();
  }

  static void observe(String stream, Socket socket) {
    try {
      String first = readFrame(socket.getInputStream()); if (first == null) return;
      subscriptions.add(json("stream", stream, "payload", first));
      if ("trade".equals(stream)) {
        writeFrame(socket, "{\"stream\":\"authorization\",\"data\":{\"status\":\"authorized\"}}", true);
        String subscription = readFrame(socket.getInputStream());
        if (subscription != null) {
          subscriptions.add(json("stream", stream, "payload", subscription));
          writeFrame(socket, "{\"stream\":\"listening\",\"data\":{\"streams\":[\"trade_updates\"]}}", true);
        }
      }
    } catch (Exception ignored) { sockets.get(stream).remove(socket); }
  }

  static void broadcast(String target, String payload) {
    String stream = switch (target) { case "news" -> "news"; case "market" -> "market"; default -> "trade"; };
    for (Socket socket : List.copyOf(sockets.get(stream))) try { writeFrame(socket, payload, "trade".equals(stream)); } catch (Exception e) { sockets.get(stream).remove(socket); }
  }

  static void writeFrame(Socket socket, String payload, boolean binary) throws IOException {
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8); OutputStream output = socket.getOutputStream(); output.write(binary ? 130 : 129);
    if (bytes.length < 126) output.write(bytes.length); else { output.write(126); output.write(bytes.length >>> 8); output.write(bytes.length); }
    output.write(bytes); output.flush();
  }

  static Map<String,String> headers(InputStream input) throws IOException { Map<String,String> result = new HashMap<>(); String line; while (!(line = line(input)).isEmpty()) { int split = line.indexOf(':'); if (split > 0) result.put(line.substring(0, split).toLowerCase(), line.substring(split + 1).trim()); } return result; }
  static String line(InputStream input) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); int value; while ((value = input.read()) >= 0 && value != '\n') if (value != '\r') out.write(value); return out.toString(StandardCharsets.US_ASCII); }
  static String readFrame(InputStream input) throws IOException { int first = input.read(), second = input.read(); if (first < 0 || second < 0) return null; int length = second & 127; if (length == 126) length = (input.read() << 8) | input.read(); byte[] mask = new byte[4]; if ((second & 128) != 0) input.readNBytes(mask, 0, 4); byte[] payload = input.readNBytes(length); for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4]; return new String(payload, StandardCharsets.UTF_8); }
  static void record(HttpExchange exchange) throws IOException { String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); requests.add("{\"method\":\"" + exchange.getRequestMethod() + "\",\"path\":\"" + exchange.getRequestURI().getPath() + "\",\"body\":" + quote(body) + ",\"authorization\":" + quote(exchange.getRequestHeaders().getFirst("Authorization")) + "}"); }
  static String account() { return "{\"id\":\"" + ACCOUNT_ID + "\",\"account_number\":\"IT-001\",\"status\":\"ACTIVE\",\"currency\":\"USD\",\"cash\":\"10000\",\"buying_power\":\"20000\",\"portfolio_value\":\"10000\",\"equity\":\"10000\",\"last_equity\":\"10000\",\"long_market_value\":\"540\",\"non_marginable_buying_power\":\"10000\",\"account_blocked\":false,\"trading_blocked\":false,\"trade_suspended_by_user\":false,\"shorting_enabled\":true}"; }
  static String formValue(String form, String name) { for (String pair : form.split("&")) { String[] parts = pair.split("=", 2); if (parts.length == 2 && name.equals(URLDecoder.decode(parts[0], StandardCharsets.UTF_8))) return URLDecoder.decode(parts[1], StandardCharsets.UTF_8); } return ""; }
  static String param(String query, String name) { if (query == null) return ""; return formValue(query, name); }
  static String quote(String value) { return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\""; }
  static String json(String key, String value) { return "{\"" + key + "\":" + quote(value) + "}"; }
  static String json(String firstKey, String firstValue, String secondKey, String secondValue) {
    return "{\"" + firstKey + "\":" + quote(firstValue) + ",\"" + secondKey + "\":" + quote(secondValue) + "}";
  }
  static void reply(HttpExchange exchange, int status, String body) throws IOException { exchange.getResponseHeaders().set("Content-Type", "application/json"); byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); }
  static String jwks() { RSAPublicKey key = (RSAPublicKey) pair.getPublic(); return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"it-key\",\"alg\":\"RS256\",\"n\":\"" + b64(key.getModulus().toByteArray()) + "\",\"e\":\"" + b64(key.getPublicExponent().toByteArray()) + "\"}]}"; }
  static String jwt(String role) { try { String header = b64("{\"alg\":\"RS256\",\"kid\":\"it-key\"}".getBytes(StandardCharsets.UTF_8)); String roles = role == null || role.isBlank() ? "[]" : "[\"" + role + "\"]"; String payload = b64(("{\"iss\":\"http://127.0.0.1:18080/issuer\",\"sub\":\"integration-user\",\"email\":\"it@example.invalid\",\"preferred_username\":\"integration-user\",\"realm_access\":{\"roles\":" + roles + "},\"exp\":" + (Instant.now().getEpochSecond() + 3600) + "}").getBytes(StandardCharsets.UTF_8)); Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(pair.getPrivate()); signature.update((header + "." + payload).getBytes(StandardCharsets.UTF_8)); return header + "." + payload + "." + b64(signature.sign()); } catch (Exception e) { throw new IllegalStateException(e); } }
  static String b64(byte[] bytes) { if (bytes.length > 1 && bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
