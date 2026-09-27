package com.ai_got_this.account_service.client;

import com.ai_got_this.account_service.config.AlpacaAPIProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Client for the installation's one configured Alpaca paper account. */
@Component
public class AlpacaTradingClient {
    private final RestTemplate restTemplate;
    private final AlpacaAPIProperties properties;

    public AlpacaTradingClient(RestTemplate restTemplate, AlpacaAPIProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public BrokerAccount account() {
        return restTemplate.exchange(
                url("/v2/account"),
                HttpMethod.GET,
                new HttpEntity<Void>(headers()),
                BrokerAccount.class
        ).getBody();
    }

    public List<BrokerPosition> positions() {
        BrokerPosition[] result = restTemplate.exchange(
                url("/v2/positions"),
                HttpMethod.GET,
                new HttpEntity<Void>(headers()),
                BrokerPosition[].class
        ).getBody();
        return result == null ? List.of() : List.of(result);
    }

    public List<BrokerWatchlist> watchlists() {
        BrokerWatchlist[] result = restTemplate.exchange(
                url("/v2/watchlists"),
                HttpMethod.GET,
                new HttpEntity<Void>(headers()),
                BrokerWatchlist[].class
        ).getBody();
        return result == null ? List.of() : List.of(result);
    }

    public BrokerWatchlist createWatchlist(String name, List<String> symbols) {
        return restTemplate.exchange(
                url("/v2/watchlists"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "symbols", symbols), headers()),
                BrokerWatchlist.class
        ).getBody();
    }

    public BrokerWatchlist replaceWatchlist(UUID id, String name, List<String> symbols) {
        return restTemplate.exchange(
                url("/v2/watchlists/" + id),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", name, "symbols", symbols), headers()),
                BrokerWatchlist.class
        ).getBody();
    }

    public void addWatchlistAsset(UUID id, String symbol) {
        restTemplate.exchange(
                url("/v2/watchlists/" + id),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("symbol", symbol), headers()),
                Void.class
        );
    }

    public void removeWatchlistAsset(UUID id, String symbol) {
        restTemplate.exchange(
                url("/v2/watchlists/" + id + "/" + symbol),
                HttpMethod.DELETE,
                new HttpEntity<Void>(headers()),
                Void.class
        );
    }

    public void deleteWatchlist(UUID id) {
        restTemplate.exchange(
                url("/v2/watchlists/" + id),
                HttpMethod.DELETE,
                new HttpEntity<Void>(headers()),
                Void.class
        );
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("APCA-API-KEY-ID", properties.key());
        headers.set("APCA-API-SECRET-KEY", properties.secret());
        return headers;
    }

    private String url(String path) {
        return properties.paperTradingBaseUrl().replaceAll("/$", "") + path;
    }

    public record BrokerAccount(
            UUID id,
            String account_number,
            String status,
            String currency,
            BigDecimal cash,
            BigDecimal buying_power,
            BigDecimal non_marginable_buying_power,
            BigDecimal portfolio_value,
            BigDecimal equity,
            BigDecimal last_equity,
            BigDecimal long_market_value,
            boolean account_blocked,
            boolean trading_blocked,
            boolean trade_suspended_by_user,
            boolean shorting_enabled
    ) {}

    public record BrokerPosition(
            UUID asset_id,
            String symbol,
            BigDecimal qty,
            BigDecimal avg_entry_price,
            BigDecimal market_value,
            BigDecimal cost_basis,
            BigDecimal current_price,
            BigDecimal unrealized_pl,
            String side
    ) {}

    public record BrokerWatchlist(UUID id, String name, List<BrokerAsset> assets) {}

    public record BrokerAsset(String symbol) {}
}
