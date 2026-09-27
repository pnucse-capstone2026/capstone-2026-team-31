#!/usr/bin/env python3
"""Lightweight RabbitMQ mock of ``AI/price_prediction_service/consumer.py``.

It preserves that worker's topology, buffering, payload behavior, and message
lifecycle. TensorFlow/scaler inference is replaced with a random probability.
"""

from __future__ import annotations

import json
import math
import os
import random
import time
import uuid
from datetime import datetime, timezone
from collections import defaultdict, deque
from typing import Any

try:
    import pika
except ModuleNotFoundError:  # Allows contract testing without RabbitMQ installed.
    pika = None


# Environment names and defaults match the production worker exactly.
RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", 5672))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "guest")
RABBITMQ_VHOST = os.getenv("RABBITMQ_VHOST", "/")
RABBITMQ_MARKET_TICKS_QUEUE = os.getenv("RABBITMQ_MARKET_TICKS_QUEUE", "market-data")
RABBITMQ_NEWS_SENTIMENT_QUEUE = os.getenv("RABBITMQ_NEWS_SENTIMENT_QUEUE", "news-sentiment")
RABBITMQ_PREDICTIONS_QUEUE = os.getenv("RABBITMQ_PREDICTIONS_QUEUE", "predictions")
RABBITMQ_PREDICTIONS_EXCHANGE = os.getenv("RABBITMQ_PREDICTIONS_EXCHANGE", "predictions-exchange")
RABBITMQ_QUEUE_DURABLE = os.getenv("RABBITMQ_QUEUE_DURABLE", "true").lower() == "true"
RABBITMQ_MARKET_DATA_EXCHANGE = os.getenv("RABBITMQ_MARKET_DATA_EXCHANGE", "market-data-exchange")
RABBITMQ_MARKET_DATA_ROUTING_KEY = os.getenv("RABBITMQ_MARKET_DATA_ROUTING_KEY", "market-data")
RABBITMQ_NEWS_SENTIMENT_EXCHANGE = os.getenv("RABBITMQ_NEWS_SENTIMENT_EXCHANGE", "news-sentiment-exchange")
RABBITMQ_NEWS_SENTIMENT_ROUTING_KEY = os.getenv("RABBITMQ_NEWS_SENTIMENT_ROUTING_KEY", "news-sentiment")
MOCK_INITIAL_TICKER = os.getenv("MOCK_INITIAL_TICKER", "AAPL")
MOCK_INITIAL_PRICE = float(os.getenv("MOCK_INITIAL_PRICE", "100.0"))

# Feature names and window length from the current pinned production metadata.
SEQUENCE_LENGTH = 20
PRICE_FEATURES = (
    "Open", "High", "Low", "Close", "Volume", "Return", "MA_5", "MA_20",
    "Volatility_5", "RSI_14", "MACD",
)

_random_seed = os.getenv("MOCK_RANDOM_SEED")
RANDOM = random.Random(_random_seed) if _random_seed is not None else random.Random()


def prediction_overrides() -> dict[str, float]:
    """Read deterministic probability fixtures keyed by ticker.

    ``MOCK_PREDICTION_OVERRIDES`` is a JSON object such as
    ``{"AAPL": 0.9, "MSFT": 0.1, "HOLD": 0.6}``. A listed ticker always
    emits that probability; other symbols retain the existing random behavior.
    """
    raw = os.getenv("MOCK_PREDICTION_OVERRIDES", "{}")
    try:
        values = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise RuntimeError("MOCK_PREDICTION_OVERRIDES must be valid JSON") from exc
    if not isinstance(values, dict):
        raise RuntimeError("MOCK_PREDICTION_OVERRIDES must be a JSON object")

    result: dict[str, float] = {}
    for ticker, probability in values.items():
        normalized = str(ticker).strip().upper()
        try:
            parsed = float(probability)
        except (TypeError, ValueError) as exc:
            raise RuntimeError("mock prediction probabilities must be numbers") from exc
        if not normalized or not math.isfinite(parsed) or not 0.0 <= parsed <= 1.0:
            raise RuntimeError("mock prediction probabilities must be finite values from 0.0 through 1.0")
        result[normalized] = parsed
    return result


PREDICTION_OVERRIDES = prediction_overrides()

price_buffer: defaultdict[str, deque[dict[str, Any]]] = defaultdict(
    lambda: deque(maxlen=SEQUENCE_LENGTH)
)
latest_news: dict[str, dict[str, float]] = {}


def build_mock_history() -> list[dict[str, float]]:
    """Create a valid synthetic window using the production feature schema."""
    history: list[dict[str, float]] = []
    for index in range(SEQUENCE_LENGTH):
        close = MOCK_INITIAL_PRICE + index * 0.1
        history.append(
            {
                "Open": close - 0.05,
                "High": close + 0.15,
                "Low": close - 0.2,
                "Close": close,
                "Volume": float(1_000_000 + index * 1_000),
                "Return": 0.001,
                "MA_5": close - 0.1,
                "MA_20": close - 0.3,
                "Volatility_5": 0.01,
                "RSI_14": 50.0,
                "MACD": 0.05,
            }
        )
    return history


def seed_price_history(ticker: str) -> None:
    """Seed a newly observed ticker once, before its live event is processed.

    The deque remains bounded at 20. Therefore, a live market bar immediately
    replaces the oldest seed bar, and later market bars replace the remainder
    in arrival order. News events use the seeded window but do not alter it.
    """
    bars = price_buffer[ticker]
    if not bars:
        bars.extend(build_mock_history())


# Retain the debug mock's ready-to-predict default ticker at process startup.
seed_price_history(MOCK_INITIAL_TICKER)


def apply_sentiment_decay(sentiment_data: dict[str, float] | None) -> tuple[float, float, float]:
    """Apply the real worker's sentiment-decay calculation."""
    if not sentiment_data:
        return 0.0, 0.0, 0.0

    elapsed_minutes = (time.time() - sentiment_data["timestamp"]) / 60.0
    decay_factor = math.exp(-0.023 * elapsed_minutes)
    news_sentiment = sentiment_data["sentiment"]
    impact_confidence = sentiment_data["confidence"] * decay_factor
    return news_sentiment, impact_confidence, news_sentiment * impact_confidence


def mock_lstm_probability(ticker: str) -> float:
    """The only mock: a replacement for the LSTM sigmoid output."""
    return PREDICTION_OVERRIDES.get(ticker, RANDOM.uniform(0.0, 1.0))


def process_prediction(ticker: str, channel: Any, triggered_by: str) -> None:
    """Process a full bar window and publish the production payload contract."""
    bars = price_buffer.get(ticker)
    if bars is None or len(bars) < SEQUENCE_LENGTH:
        return

    latest_price = float(bars[-1]["Close"])
    if not math.isfinite(latest_price) or latest_price <= 0:
        print(f" [x] Skipping prediction for {ticker}: invalid latest close {latest_price!r}")
        return

    # Reproduce the production feature extraction/type conversion so malformed
    # bar behavior remains the same. Only scaler/model evaluation is omitted.
    price_array = [[float(bar[feature]) for feature in PRICE_FEATURES] for bar in bars]
    s_val, s_conf, s_weighted = apply_sentiment_decay(latest_news.get(ticker))
    news_array = [[s_val, s_conf, s_weighted]]
    del price_array, news_array

    probability_up = mock_lstm_probability(ticker)
    direction = "UP" if probability_up >= 0.5 else "DOWN"
    confidence = probability_up if probability_up >= 0.5 else 1 - probability_up
    prediction_payload = {
        "predictionId": str(uuid.uuid4()),
        "ticker": ticker,
        "direction": direction,
        "probabilityUp": round(probability_up, 4),
        "confidence": round(confidence, 4),
        "latestPrice": latest_price,
        "triggeredBy": triggered_by,
        "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "modelVersion": "fake-price-prediction-v1",
    }
    channel.basic_publish(
        exchange=RABBITMQ_PREDICTIONS_EXCHANGE,
        routing_key=RABBITMQ_PREDICTIONS_QUEUE,
        body=json.dumps(prediction_payload),
    )
    print(f" [x] Published prediction for {ticker}: {direction} ({confidence * 100:.1f}%)")


def declare_topology(channel: Any) -> None:
    """Declare and bind precisely the topology declared by the real worker."""
    channel.exchange_declare(
        exchange=RABBITMQ_MARKET_DATA_EXCHANGE,
        exchange_type="direct",
        durable=RABBITMQ_QUEUE_DURABLE,
    )
    channel.exchange_declare(
        exchange=RABBITMQ_NEWS_SENTIMENT_EXCHANGE,
        exchange_type="direct",
        durable=RABBITMQ_QUEUE_DURABLE,
    )
    channel.queue_declare(queue=RABBITMQ_MARKET_TICKS_QUEUE, durable=RABBITMQ_QUEUE_DURABLE)
    channel.queue_declare(queue=RABBITMQ_NEWS_SENTIMENT_QUEUE, durable=RABBITMQ_QUEUE_DURABLE)
    channel.queue_declare(queue=RABBITMQ_PREDICTIONS_QUEUE, durable=RABBITMQ_QUEUE_DURABLE)
    if RABBITMQ_PREDICTIONS_EXCHANGE:
        channel.exchange_declare(
            exchange=RABBITMQ_PREDICTIONS_EXCHANGE,
            exchange_type="direct",
            durable=RABBITMQ_QUEUE_DURABLE,
        )
        channel.queue_bind(
            exchange=RABBITMQ_PREDICTIONS_EXCHANGE,
            queue=RABBITMQ_PREDICTIONS_QUEUE,
            routing_key=RABBITMQ_PREDICTIONS_QUEUE,
        )
    channel.queue_bind(
        exchange=RABBITMQ_MARKET_DATA_EXCHANGE,
        queue=RABBITMQ_MARKET_TICKS_QUEUE,
        routing_key=RABBITMQ_MARKET_DATA_ROUTING_KEY,
    )
    channel.queue_bind(
        exchange=RABBITMQ_NEWS_SENTIMENT_EXCHANGE,
        queue=RABBITMQ_NEWS_SENTIMENT_QUEUE,
        routing_key=RABBITMQ_NEWS_SENTIMENT_ROUTING_KEY,
    )


def on_market_tick(channel: Any, _method: Any, _properties: Any, body: bytes) -> None:
    """Consume one market-data message, matching the real callback."""
    tick = json.loads(body)
    ticker = str(tick["ticker"]).strip().upper()
    seed_price_history(ticker)
    price_buffer[ticker].append(tick["bar"])
    process_prediction(ticker, channel, "market_tick")


def on_news_sentiment(channel: Any, _method: Any, _properties: Any, body: bytes) -> None:
    """Consume one news-sentiment message, matching the real callback."""
    news = json.loads(body)
    ticker = str(news["ticker"]).strip().upper()
    seed_price_history(ticker)
    latest_news[ticker] = {
        "sentiment": 1 if news.get("label") == 1 else -1,
        "confidence": news.get("confidence", 0.5),
        "timestamp": time.time(),
    }
    print(f" [*] Updated sentiment for {ticker}")
    process_prediction(ticker, channel, "news_sentiment")


def require_pika() -> Any:
    if pika is None:
        raise RuntimeError(
            "pika is required to run the consumer. Install dependencies with "
            "python3 -m pip install -r fake-price-prediction-service-requirements.txt."
        )
    return pika


def main() -> None:
    pika_module = require_pika()
    credentials = pika_module.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    connection = pika_module.BlockingConnection(
        pika_module.ConnectionParameters(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            virtual_host=RABBITMQ_VHOST,
            credentials=credentials,
        )
    )
    channel = connection.channel()
    declare_topology(channel)
    channel.basic_consume(
        queue=RABBITMQ_MARKET_TICKS_QUEUE,
        on_message_callback=on_market_tick,
        auto_ack=True,
    )
    channel.basic_consume(
        queue=RABBITMQ_NEWS_SENTIMENT_QUEUE,
        on_message_callback=on_news_sentiment,
        auto_ack=True,
    )
    print(
        " [*] Prediction Service listening on "
        f"'{RABBITMQ_MARKET_TICKS_QUEUE}' & '{RABBITMQ_NEWS_SENTIMENT_QUEUE}'. "
        "Press CTRL+C to exit."
    )
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        channel.stop_consuming()
        connection.close()


if __name__ == "__main__":
    main()
