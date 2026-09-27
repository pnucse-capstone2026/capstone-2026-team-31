#!/usr/bin/env python3
"""One-shot RabbitMQ mock for the news-ingestion-service.

At process startup the mock declares the production financial-news topology
and publishes one fixed headline.  It then stays alive, but deliberately never
publishes again--including if the RabbitMQ connection later goes away.
"""

from __future__ import annotations

import json
import logging
import os
import time
from typing import Any

try:
    import pika
except ModuleNotFoundError:  # Lets the pure unit tests run without pika installed.
    pika = None


logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
LOGGER = logging.getLogger("fake_news_ingestion_service")

# These are the Spring Boot service's connection environment variables and
# defaults, so this mock can replace it without changing deployment settings.
RABBITMQ_HOST = os.getenv("SPRING_RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("SPRING_RABBITMQ_PORT", "5672"))
RABBITMQ_USERNAME = os.getenv("SPRING_RABBITMQ_USERNAME", "trading-bot-dev")
RABBITMQ_PASSWORD = os.getenv("SPRING_RABBITMQ_PASSWORD", "1234")
RABBITMQ_VHOST = os.getenv("SPRING_RABBITMQ_VIRTUAL_HOST", "trading-bot-dev-vhost")
RABBITMQ_RETRY_DELAY_SECONDS = float(os.getenv("RABBITMQ_RETRY_DELAY_SECONDS", "5"))

FINANCIAL_NEWS_EXCHANGE = "financial-news-exchange"
FINANCIAL_NEWS_QUEUE = "financial-news"
FINANCIAL_NEWS_ROUTING_KEY = "financial-news"
FIXED_HEADLINE = {
    "ticker": "AAPL",
    "headline": "Apple reports stronger-than-expected quarterly earnings.",
}


def require_pika() -> Any:
    """Return pika or explain how to install the runtime dependency."""
    if pika is None:
        raise RuntimeError(
            "pika is required to run this mock. Install dependencies with "
            "python3 -m pip install -r fake-news-ingestion-service-requirements.txt."
        )
    return pika


def create_connection() -> Any:
    """Open a RabbitMQ connection using the real service's configuration."""
    pika_module = require_pika()
    credentials = pika_module.PlainCredentials(RABBITMQ_USERNAME, RABBITMQ_PASSWORD)
    return pika_module.BlockingConnection(
        pika_module.ConnectionParameters(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            virtual_host=RABBITMQ_VHOST,
            credentials=credentials,
            heartbeat=60,
            blocked_connection_timeout=300,
        )
    )


def declare_topology(channel: Any) -> None:
    """Declare the same durable direct-exchange binding as the real service."""
    channel.exchange_declare(
        exchange=FINANCIAL_NEWS_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )
    channel.queue_declare(queue=FINANCIAL_NEWS_QUEUE, durable=True)
    channel.queue_bind(
        exchange=FINANCIAL_NEWS_EXCHANGE,
        queue=FINANCIAL_NEWS_QUEUE,
        routing_key=FINANCIAL_NEWS_ROUTING_KEY,
    )


class StartupHeadlinePublisher:
    """Publishes at most one headline for this Python process lifetime."""

    def __init__(self) -> None:
        self.published = False

    def publish_once(self, channel: Any) -> bool:
        """Publish the immutable startup payload, unless it was already sent."""
        if self.published:
            return False

        pika_module = require_pika()
        channel.basic_publish(
            exchange=FINANCIAL_NEWS_EXCHANGE,
            routing_key=FINANCIAL_NEWS_ROUTING_KEY,
            body=json.dumps(FIXED_HEADLINE),
            properties=pika_module.BasicProperties(
                content_type="application/json",
                delivery_mode=2,
            ),
        )
        self.published = True
        LOGGER.info("Published the one startup headline: %s", FIXED_HEADLINE)
        return True


def connect_and_publish_once(publisher: StartupHeadlinePublisher) -> Any:
    """Keep trying only until the single startup publish succeeds.

    Retrying before a successful connection is necessary for normal container
    startup ordering.  Once ``published`` is true this function is never
    called again, which prevents reconnects from generating another message.
    """
    while not publisher.published:
        connection = None
        try:
            connection = create_connection()
            channel = connection.channel()
            declare_topology(channel)
            publisher.publish_once(channel)
            return connection
        except KeyboardInterrupt:
            if connection is not None:
                connection.close()
            raise
        except Exception as exc:
            if connection is not None:
                try:
                    connection.close()
                except Exception:
                    pass
            LOGGER.warning(
                "Could not publish startup headline; retrying in %ss: %s",
                RABBITMQ_RETRY_DELAY_SECONDS,
                exc,
            )
            time.sleep(RABBITMQ_RETRY_DELAY_SECONDS)

    raise AssertionError("startup publisher was already marked as published")


def stay_alive(connection: Any) -> None:
    """Keep the container up while maintaining heartbeats without republishing."""
    while True:
        try:
            connection.process_data_events(time_limit=1)
        except Exception as exc:
            # The headline has already been sent. Intentionally do not reconnect:
            # reconnecting via the startup path could violate the one-message rule.
            LOGGER.warning("RabbitMQ connection ended after startup publish: %s", exc)
            while True:
                time.sleep(60)


def main() -> None:
    publisher = StartupHeadlinePublisher()
    connection = connect_and_publish_once(publisher)
    try:
        stay_alive(connection)
    except KeyboardInterrupt:
        LOGGER.info("Fake news-ingestion-service stopped")
    finally:
        try:
            connection.close()
        except Exception:
            pass


if __name__ == "__main__":
    main()
