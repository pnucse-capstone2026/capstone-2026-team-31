#!/usr/bin/env python3
"""Lightweight RabbitMQ-compatible mock of the news-impact-service.

The real service consumes a JSON ticker/headline payload from the financial-news
queue and publishes a ticker/label/confidence payload to the news-sentiment
queue. This mock preserves that queue contract and RabbitMQ topology while
replacing FinBERT inference with a random, logically valid binary prediction.
"""

from __future__ import annotations

import json
import logging
import os
import random
import time
from typing import Any

try:
    import pika
except ModuleNotFoundError:  # Allows the prediction function to be used without RabbitMQ installed.
    pika = None


def env_bool(name: str, default: str) -> bool:
    return os.getenv(name, default).lower() in ("true", "1", "yes")


logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
LOGGER = logging.getLogger("fake_news_impact_service")


# Keep the real service's RabbitMQ defaults and environment-variable interface.
RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "trading-bot-dev")
RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD", "1234")
RABBITMQ_VHOST = os.getenv("RABBITMQ_VHOST", "trading-bot-dev-vhost")
RABBITMQ_HEARTBEAT = int(os.getenv("RABBITMQ_HEARTBEAT", "60"))
RABBITMQ_BLOCKED_CONNECTION_TIMEOUT = int(
    os.getenv("RABBITMQ_BLOCKED_CONNECTION_TIMEOUT", "300")
)
RABBITMQ_RETRY_DELAY_SECONDS = int(
    os.getenv("RABBITMQ_RETRY_DELAY_SECONDS", "5")
)

RABBITMQ_INPUT_EXCHANGE_TYPE = os.getenv("RABBITMQ_INPUT_EXCHANGE_TYPE", "direct")
RABBITMQ_OUTPUT_EXCHANGE_TYPE = os.getenv("RABBITMQ_OUTPUT_EXCHANGE_TYPE", "direct")
RABBITMQ_INPUT_EXCHANGE_DURABLE = env_bool(
    "RABBITMQ_INPUT_EXCHANGE_DURABLE", "true"
)
RABBITMQ_OUTPUT_EXCHANGE_DURABLE = env_bool(
    "RABBITMQ_OUTPUT_EXCHANGE_DURABLE", "true"
)
RABBITMQ_INPUT_QUEUE_DURABLE = env_bool("RABBITMQ_INPUT_QUEUE_DURABLE", "true")
RABBITMQ_OUTPUT_QUEUE_DURABLE = env_bool("RABBITMQ_OUTPUT_QUEUE_DURABLE", "true")
RABBITMQ_OUTPUT_DELIVERY_MODE = int(
    os.getenv("RABBITMQ_OUTPUT_DELIVERY_MODE", "2")
)

INPUT_EXCHANGE = os.getenv("RABBITMQ_INPUT_EXCHANGE", "financial-news-exchange")
INPUT_QUEUE = os.getenv("RABBITMQ_INPUT_QUEUE", "financial-news")
INPUT_ROUTING_KEY = os.getenv("RABBITMQ_INPUT_ROUTING_KEY", "financial-news")
OUTPUT_EXCHANGE = os.getenv("RABBITMQ_OUTPUT_EXCHANGE", "news-sentiment-exchange")
OUTPUT_QUEUE = os.getenv("RABBITMQ_OUTPUT_QUEUE", "news-sentiment")
OUTPUT_ROUTING_KEY = os.getenv("RABBITMQ_OUTPUT_ROUTING_KEY", "news-sentiment")

_random_seed = os.getenv("MOCK_RANDOM_SEED")
RANDOM = random.Random(_random_seed) if _random_seed is not None else random.Random()


def mock_prediction(ticker: str, headline: str) -> dict[str, Any]:
    """Return a valid replacement for the real FinBERT prediction payload.

    The real binary classifier maps LABEL_1 to label 1 and every other label
    to 0. Its returned score is the winning-class probability, so the logical
    range for a binary classifier is 0.5 through 1.0.
    """
    normalized_ticker = ticker.upper().strip()
    headline.strip()  # Preserve the real service's input validation behavior.

    return {
        "ticker": normalized_ticker,
        "label": RANDOM.randint(0, 1),
        "confidence": round(RANDOM.uniform(0.5, 1.0), 4),
    }


def require_pika() -> Any:
    if pika is None:
        raise RuntimeError(
            "pika is required to run the consumer. Install dependencies with "
            "python3 -m pip install -r fake-news-impact-service-requirements.txt."
        )
    return pika


def create_rabbitmq_connection() -> Any:
    pika_module = require_pika()
    credentials = pika_module.PlainCredentials(
        username=RABBITMQ_USER,
        password=RABBITMQ_PASSWORD,
    )
    return pika_module.BlockingConnection(
        pika_module.ConnectionParameters(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            virtual_host=RABBITMQ_VHOST,
            credentials=credentials,
            heartbeat=RABBITMQ_HEARTBEAT,
            blocked_connection_timeout=RABBITMQ_BLOCKED_CONNECTION_TIMEOUT,
        )
    )


def declare_topology(channel: Any) -> None:
    """Declare the same input and output exchanges, queues, and bindings."""
    channel.exchange_declare(
        exchange=INPUT_EXCHANGE,
        exchange_type=RABBITMQ_INPUT_EXCHANGE_TYPE,
        durable=RABBITMQ_INPUT_EXCHANGE_DURABLE,
    )
    channel.queue_declare(queue=INPUT_QUEUE, durable=RABBITMQ_INPUT_QUEUE_DURABLE)
    channel.queue_bind(
        exchange=INPUT_EXCHANGE,
        queue=INPUT_QUEUE,
        routing_key=INPUT_ROUTING_KEY,
    )

    channel.exchange_declare(
        exchange=OUTPUT_EXCHANGE,
        exchange_type=RABBITMQ_OUTPUT_EXCHANGE_TYPE,
        durable=RABBITMQ_OUTPUT_EXCHANGE_DURABLE,
    )
    channel.queue_declare(
        queue=OUTPUT_QUEUE,
        durable=RABBITMQ_OUTPUT_QUEUE_DURABLE,
    )
    channel.queue_bind(
        exchange=OUTPUT_EXCHANGE,
        queue=OUTPUT_QUEUE,
        routing_key=OUTPUT_ROUTING_KEY,
    )


def publish_prediction(channel: Any, prediction: dict[str, Any]) -> None:
    pika_module = require_pika()
    channel.basic_publish(
        exchange=OUTPUT_EXCHANGE,
        routing_key=OUTPUT_ROUTING_KEY,
        body=json.dumps(prediction),
        properties=pika_module.BasicProperties(
            content_type="application/json",
            delivery_mode=RABBITMQ_OUTPUT_DELIVERY_MODE,
        ),
    )


def process_message(channel: Any, body: bytes) -> None:
    """Process one payload with the real service's auto-ack semantics."""
    try:
        message = json.loads(body.decode("utf-8"))
        prediction = mock_prediction(
            ticker=message["ticker"],
            headline=message["headline"],
        )
        publish_prediction(channel, prediction)
        LOGGER.info(
            "Published mock prediction for %s: label=%s confidence=%.4f",
            prediction["ticker"],
            prediction["label"],
            prediction["confidence"],
        )
    except Exception as exc:
        # The real service consumes with auto_ack=True: bad messages are logged
        # and are not requeued.
        LOGGER.exception(
            "Error processing RabbitMQ message (auto-ack=true): %s",
            exc,
        )


def rabbitmq_consumer() -> None:
    """Reconnect indefinitely, matching the real service's consumer lifecycle."""
    while True:
        try:
            connection = create_rabbitmq_connection()
            channel = connection.channel()
            declare_topology(channel)

            LOGGER.info(
                "Connection with RabbitMQ established. Waiting for messages from queue '%s'...",
                INPUT_QUEUE,
            )

            channel.basic_consume(
                queue=INPUT_QUEUE,
                on_message_callback=lambda channel, _method, _properties, body: process_message(
                    channel, body
                ),
                auto_ack=True,
            )
            channel.start_consuming()
        except KeyboardInterrupt:
            LOGGER.info("Fake news-impact-service stopped")
            return
        except Exception as exc:
            LOGGER.exception("RabbitMQ connection error: %s", exc)
            LOGGER.info(
                "Retrying connection in %s seconds...",
                RABBITMQ_RETRY_DELAY_SECONDS,
            )
            time.sleep(RABBITMQ_RETRY_DELAY_SECONDS)


if __name__ == "__main__":
    rabbitmq_consumer()
