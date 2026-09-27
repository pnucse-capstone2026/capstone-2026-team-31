#!/usr/bin/env python3
"""Controlled backend integration suite.  It never calls a public endpoint."""
import base64
import json
import os
import re
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
IT = ROOT / "integration-tests"
ARTIFACTS = IT / "artifacts"
ARTIFACTS.mkdir(exist_ok=True)
RUN_ID = "it-" + uuid.uuid4().hex[:12]
PASSED, FAILED, PROCESSES = [], [], []

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return None

def request(url, method="GET", body=None, headers=None, no_redirect=False):
    data = None if body is None else (body if isinstance(body, bytes) else json.dumps(body).encode())
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json", **(headers or {})})
    opener = urllib.request.build_opener(NoRedirect()) if no_redirect else urllib.request.build_opener()
    try:
        with opener.open(req, timeout=5) as response:
            raw = response.read()
            return response.status, json.loads(raw or b"{}"), dict(response.headers)
    except urllib.error.HTTPError as error:
        raw = error.read()
        try: payload = json.loads(raw or b"{}")
        except ValueError: payload = {"raw": raw.decode(errors="replace")[:500]}
        return error.code, payload, dict(error.headers)

def fake(path, method="GET", body=None, headers=None):
    return request("http://127.0.0.1:18080" + path, method, body, headers)

def rabbit(path, method="GET", body=None):
    auth = base64.b64encode(b"it_user:it_password_only").decode()
    status, value, _ = request("http://127.0.0.1:15673" + path, method, body, {"Authorization": "Basic " + auth})
    if status >= 300: raise RuntimeError("RabbitMQ %s %s returned %s: %s" % (method, path, status, value))
    return value

def wait(label, predicate, seconds=45):
    deadline, last = time.monotonic() + seconds, None
    while time.monotonic() < deadline:
        try:
            if predicate():
                PASSED.append(label); return True
        except Exception as error:
            last = error
        time.sleep(0.25)
    FAILED.append((label, "Test-environment or integration defect", repr(last)))
    return False

def check(label, condition, evidence, category="Production defect"):
    if condition: PASSED.append(label)
    else: FAILED.append((label, category, str(evidence)[:2000]))
    return condition

def scenario(label, callback):
    try: callback()
    except Exception as error: FAILED.append((label, "Test-suite execution failure", repr(error)))

def queue_publish(queue, payload):
    return rabbit("/api/exchanges/integration/amq.default/publish", "POST", {
        "properties": {"content_type": "application/json"}, "routing_key": queue,
        "payload": json.dumps(payload), "payload_encoding": "string"})

def queue_get(queue, count=50):
    return rabbit("/api/queues/integration/%s/get" % queue, "POST", {
        "count": count, "ackmode": "ack_requeue_false", "encoding": "auto", "truncate": 50000})

def log_contains(name, text):
    path = ARTIFACTS / (name + ".log")
    return path.exists() and text in path.read_text(errors="replace")

def start(name, folder, environment, command=None):
    log = open(ARTIFACTS / (name + ".log"), "w")
    executable = command or ["java", "-jar", str(next((ROOT / folder / "target").glob("*.jar")))]
    process = subprocess.Popen(executable, cwd=ROOT / folder, env={**os.environ, **environment}, stdout=log, stderr=subprocess.STDOUT)
    PROCESSES.append((process, log)); return process

def package_all():
    for service in ["news-ingestion-service", "market-data-service", "account-service", "order-execution-service", "order-execution-persister"]:
        subprocess.run(["./mvnw", "package", "-q", "-DskipTests"], cwd=ROOT / service, check=True)

def start_services():
    common = {"SPRING_RABBITMQ_HOST": "127.0.0.1", "SPRING_RABBITMQ_PORT": "5673", "SPRING_RABBITMQ_USERNAME": "it_user", "SPRING_RABBITMQ_PASSWORD": "it_password_only", "SPRING_RABBITMQ_VIRTUAL_HOST": "integration"}
    alpaca = {"ALPACA_API_KEY": "fake-key", "ALPACA_API_SECRET": "fake-secret", "ALPACA_PAPER_TRADING_BASE_URL": "http://127.0.0.1:18080"}
    start("account", "account-service", {**common, **alpaca, "DATABASE_URL": "jdbc:postgresql://127.0.0.1:15432/accounts_it", "DATABASE_USERNAME": "accounts_it", "DATABASE_PASSWORD": "accounts_password_only", "ORDER_EXECUTION_PERSISTER_INTERNAL_BASE_URL": "http://127.0.0.1:8085", "SERVER_PORT": "8081"})
    wait("Account Service starts and migrates", lambda: log_contains("account", "Started AccountServiceApplication"))
    wait("Configured account reconciles", lambda: request("http://127.0.0.1:8081/api/v1/account/reconcile", "POST")[0] == 200)
    start("persister", "order-execution-persister", {**common, **alpaca, "DATABASE_URL": "jdbc:postgresql://127.0.0.1:15432/order_execution_it", "DATABASE_USERNAME": "accounts_it", "DATABASE_PASSWORD": "accounts_password_only", "SERVER_PORT": "8085"})
    wait("Order Persister starts and validates its schema", lambda: log_contains("persister", "Started OrderExecutionPersisterApplication"))
    start("order", "order-execution-service", {**common, **alpaca, "ALPACA_API_STREAM_URL": "ws://127.0.0.1:18083", "ALPACA_API_ORDER_ENDPOINT": "http://127.0.0.1:18080/v2/orders", "ALPACA_API_SUBSCRIPTION_MESSAGE": "{\"action\":\"listen\",\"data\":{\"streams\":[\"trade_updates\"]}}", "SERVER_PORT": "8084"})
    start("news", "news-ingestion-service", {**common, "ALPACA_API_KEY": "fake-key", "ALPACA_API_SECRET": "fake-secret", "ALPACA_API_NEWS_STREAM_URL": "ws://127.0.0.1:18081", "ALPACA_API_SUBSCRIPTION_MESSAGE": "{\"action\":\"subscribe\",\"news\":[\"*\"]}", "SERVER_PORT": "8082"})
    start("market", "market-data-service", {**common, "ALPACA_API_KEY": "fake-key", "ALPACA_API_SECRET": "fake-secret", "ALPACA_API_MARKET_STREAM_URL": "ws://127.0.0.1:18082", "ALPACA_API_SUBSCRIPTION_MESSAGE": "{\"action\":\"subscribe\",\"bars\":[\"*\"]}", "SERVER_PORT": "8083"})
    strategy_env = {"RABBITMQ_HOST": "127.0.0.1", "RABBITMQ_PORT": "5673", "RABBITMQ_USER": "it_user", "RABBITMQ_PASS": "it_password_only", "RABBITMQ_VHOST": "integration", "ACCOUNT_SERVICE_BASE_URL": "http://127.0.0.1:8081"}
    start("strategy", "AI/strategy_decision_service", strategy_env, [sys.executable, "-u", "main.py"])
    wait("Order Execution, News, Market, and Strategy start", lambda: log_contains("order", "Started OrderExecutionServiceApplication") and log_contains("news", "Started NewsIngestionServiceApplication") and log_contains("market", "Started MarketDataServiceApplication") and log_contains("strategy", "Strategy & Decision Engine listening"))

def test_topology():
    check("Order event persister fanout binding exists", len(rabbit("/api/bindings/integration/e/order-events-fanout/q/order-events-persister")) == 1, "binding missing")
    wait("All provider subscriptions are observed", lambda: len(fake("/control/subscriptions")[1]) >= 4)

def test_news():
    fake("/control/send?target=news", "POST", b'[{"T":"n","symbols":["AAPL","MSFT"],"headline":"' + RUN_ID.encode() + b'"},{"T":"n","symbols":["NVDA"],"headline":"second-' + RUN_ID.encode() + b'"}]')
    wait("News batch publishes every symbol", lambda: len(queue_get("financial-news")) == 3)
    fake("/control/send?target=news", "POST", b'[{"T":"n","symbols":[],"headline":"ignored"},{"T":"error","code":401,"msg":"denied"},{}]')
    check("Invalid news frames do not crash News Ingestion", not log_contains("news", "Application run failed"), "news service terminated")

def test_market():
    bars = []
    for index in range(27):
        close = 100.0 + index
        bars.append({"T":"b","S":"AAPL","o":close - .5,"h":close + .5,"l":close - 1,"c":close,"v":1000 + index,"t":"2026-08-22T00:%02d:00Z" % index})
    fake("/control/send?target=market", "POST", json.dumps(bars).encode())
    def valid_tick():
        ticks = queue_get("market-data")
        if not ticks: return False
        payload = json.loads(ticks[0]["payload"])
        bar = payload["bar"]
        STATE["market_tick"] = payload
        return payload["ticker"] == "AAPL" and all(key in bar for key in ["Open","High","Low","Close","Volume","Return","MA_5","MA_20","Volatility_5","RSI_14","MACD"])
    wait("Market warm-up publishes a model-compatible feature vector", valid_tick, 60)
    fake("/control/send?target=market", "POST", b'[{"T":"b","S":"AAPL","o":0,"h":1,"l":1,"c":1,"v":1}]')
    check("Invalid market bar does not crash Market Data", not log_contains("market", "Application run failed"), "market service terminated")

def test_strategy_order_projection():
    prediction = {"ticker":"AAPL","direction":"UP","confidence":0.90,"testRunId":RUN_ID}
    queue_publish("predictions", prediction)
    def order_received(): return any(item.get("path") == "/v2/orders" for item in fake("/control/requests")[1])
    wait("Strategy decision reaches the configured broker", order_received)
    requests = [item for item in fake("/control/requests")[1] if item.get("path") == "/v2/orders" and item.get("method") == "POST"]
    check("Broker request is routed once with a limit price", len(requests) == 1 and "limit_price" in requests[0].get("body", ""), requests)
    wait("Order creation event is persisted", lambda: request("http://127.0.0.1:8085/internal/v1/orders")[1] != [])
    fake("/control/send?target=trade", "POST", json.dumps({"stream":"trade_updates","data":{"event":"fill","event_id":"evt-" + RUN_ID,"order":{"id":"33333333-3333-3333-3333-333333333333","client_order_id":"fake-client-order","symbol":"AAPL","side":"buy","status":"filled","qty":"2","filled_qty":"2","filled_avg_price":"123.45","updated_at":"2026-08-22T00:01:00Z"}}}).encode())
    wait("Trade update reaches the Persister", lambda: request("http://127.0.0.1:8085/internal/v1/orders")[1] and request("http://127.0.0.1:8085/internal/v1/orders")[1][0].get("status") == "filled")
    status, orders, _ = request("http://127.0.0.1:8081/api/v1/orders")
    check("Account Service serves the shared order projection", status == 200 and orders and orders[0].get("status") == "filled", orders)

def main():
    try:
        subprocess.run(["docker", "compose", "-f", str(IT / "compose.yaml"), "up", "-d", "--wait"], check=True)
        subprocess.run(["docker", "compose", "-f", str(IT / "compose.yaml"), "run", "--rm", "order-execution-migration"], check=True)
        subprocess.run(["javac", "-d", str(IT / "fake-alpaca/classes"), str(IT / "fake-alpaca/src/FakeAlpaca.java")], check=True)
        fake_process = subprocess.Popen(["java", "-cp", str(IT / "fake-alpaca/classes"), "FakeAlpaca"]); PROCESSES.append((fake_process, None))
        scenario("infrastructure readiness", lambda: wait("Infrastructure readiness", lambda: fake("/health")[1]["status"] == "UP" and rabbit("/api/overview").get("rabbitmq_version")))
        scenario("service packaging", package_all)
        scenario("service startup", start_services)
        scenario("RabbitMQ topology", test_topology)
        scenario("news ingestion flow", test_news)
        scenario("market data flow", test_market)
        scenario("strategy, order execution, and projection flow", test_strategy_order_projection)
    except Exception as error:
        FAILED.append(("suite bootstrap", "Test-suite execution failure", repr(error)))
    finally:
        for process, stream in PROCESSES:
            if process.poll() is None: process.send_signal(signal.SIGTERM)
        for process, stream in PROCESSES:
            try: process.wait(10)
            except subprocess.TimeoutExpired: process.kill()
            if stream: stream.close()
        subprocess.run(["docker", "compose", "-f", str(IT / "compose.yaml"), "down", "-v", "--remove-orphans"], check=False)
        (ARTIFACTS / "summary.json").write_text(json.dumps({"runId":RUN_ID,"passed":PASSED,"failures":FAILED}, indent=2))
        print(json.dumps({"passed":len(PASSED),"failed":len(FAILED),"artifact":str(ARTIFACTS / "summary.json")}, indent=2))
    return 1 if FAILED else 0

if __name__ == "__main__": sys.exit(main())
