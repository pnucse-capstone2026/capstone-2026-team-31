import os
import json
import math

import httpx
import pika


RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", 5672))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "trading-bot-dev")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "1234")
RABBITMQ_VHOST = os.getenv("RABBITMQ_VHOST", "trading-bot-dev-vhost")
RABBITMQ_PREDICTIONS_QUEUE = os.getenv("RABBITMQ_PREDICTIONS_QUEUE", "predictions")
RABBITMQ_APPROVED_ORDERS_QUEUE = os.getenv(
    "RABBITMQ_APPROVED_ORDERS_QUEUE", "orders.approved"
)
ACCOUNT_SERVICE_BASE_URL = os.getenv("ACCOUNT_SERVICE_BASE_URL", "http://localhost:8081").rstrip("/")
PORTFOLIO_SERVICE_ENDPOINT = f"{ACCOUNT_SERVICE_BASE_URL}/internal/v1/portfolio"
PORTFOLIO_SERVICE_TIMEOUT_SECONDS = float(
    os.getenv("PORTFOLIO_SERVICE_TIMEOUT_SECONDS", "5.0")
)

# Decision Parameters
CONFIDENCE_THRESHOLD = 0.65   # Only trade if model is >= 65% confident
MAX_PORTFOLIO_RISK_PCT = 0.05 # Max 5% of total capital per trade

# The price-prediction contract now supplies the latest close. Keep one valid
# value per symbol so a news-triggered prediction can still be evaluated if an
# older producer omitted latestPrice.
latest_prices: dict[str, float] = {}

def fetch_portfolio():
    """Retrieve the one configured account's current portfolio snapshot."""
    try:
        response = httpx.get(
            PORTFOLIO_SERVICE_ENDPOINT,
            timeout=PORTFOLIO_SERVICE_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        return response.json()
    except (httpx.HTTPError, ValueError) as error:
        print(f" [Portfolio] Unable to retrieve portfolio: {error}")
        return None


def resolve_reference_price(prediction, ticker):
    """Return and cache a valid contract price, or use news-only cache fallback."""
    supplied_price = prediction.get("latestPrice")
    if supplied_price is not None:
        try:
            current_price = float(supplied_price)
        except (TypeError, ValueError):
            current_price = None
        if current_price is not None and math.isfinite(current_price) and current_price > 0:
            latest_prices[ticker] = current_price
            return current_price
        return None

    if prediction.get("triggeredBy") == "news_sentiment":
        return latest_prices.get(ticker)
    return None


def evaluate_trading_decisions(prediction, portfolio_response):
    """
    Improved decision rules:
    - Checks confidence threshold to avoid low-certainty trades.
    - Applies the strategy to the installation's one configured brokerage account.
    - Returns at most one order.
    """
    ticker = str(prediction["ticker"]).strip().upper()
    direction = prediction["direction"]
    confidence = prediction["confidence"]
    current_price = resolve_reference_price(prediction, ticker)

    if current_price is None:
        print(f" [Decision] {ticker}: No valid latest price. HOLD.")
        return []

    # 1. Low-confidence filter -> HOLD
    if confidence < CONFIDENCE_THRESHOLD:
        print(f" [Decision] {ticker}: Confidence {confidence:.2f} below threshold {CONFIDENCE_THRESHOLD}. HOLD.")
        return []

    account = portfolio_response.get("account")
    if not account:
        return []
    positions = portfolio_response.get("positions", [])
    if direction == "UP":
        quantity = int((float(account.get("cash") or 0) * MAX_PORTFOLIO_RISK_PCT) // current_price)
        return [] if quantity <= 0 else [{"ticker": ticker, "action": "BUY", "quantity": quantity, "order_type": "LIMIT", "limit_price": round(current_price * 1.001, 2), "referencePrice": current_price, "reason": f"LSTM UP prediction with {confidence*100:.1f}% confidence"}]
    if direction == "DOWN":
        quantity = sum(float(position.get("quantity") or 0) for position in positions if position.get("symbol") == ticker)
        return [] if quantity <= 0 else [{"ticker": ticker, "action": "SELL", "quantity": int(quantity), "order_type": "LIMIT", "limit_price": round(current_price * 0.999, 2), "referencePrice": current_price, "reason": f"LSTM DOWN prediction with {confidence*100:.1f}% confidence"}]
    return []

def main():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    connection = pika.BlockingConnection(
        pika.ConnectionParameters(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            virtual_host=RABBITMQ_VHOST,
            credentials=credentials,
        )
    )
    channel = connection.channel()

    channel.queue_declare(queue=RABBITMQ_PREDICTIONS_QUEUE, durable=True)
    channel.queue_declare(queue=RABBITMQ_APPROVED_ORDERS_QUEUE, durable=True)

    def on_prediction_received(ch, method, properties, body):
        prediction = json.loads(body)
        print(f" [*] Received prediction: {prediction}")

        portfolio = fetch_portfolio()
        if portfolio is None:
            print(" [Decision] Portfolio unavailable. Deferring prediction for retry.")
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
            return

        decisions = evaluate_trading_decisions(prediction, portfolio)

        for decision in decisions:
            # Publish order to execution service
            channel.basic_publish(
                exchange="",
                routing_key=RABBITMQ_APPROVED_ORDERS_QUEUE,
                body=json.dumps(decision)
            )
            print(f" [✓] Approved Order Published: {decision}")
        
        ch.basic_ack(delivery_tag=method.delivery_tag)

    channel.basic_consume(
        queue=RABBITMQ_PREDICTIONS_QUEUE,
        on_message_callback=on_prediction_received,
    )
    print(
        " [*] Strategy & Decision Engine listening on "
        f"'{RABBITMQ_PREDICTIONS_QUEUE}' (vhost '{RABBITMQ_VHOST}'). Press CTRL+C to exit."
    )
    
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        channel.stop_consuming()
        connection.close()

if __name__ == "__main__":
    main()
