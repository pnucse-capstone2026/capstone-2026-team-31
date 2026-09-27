"""Contract tests for the one-shot fake news ingestion service."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch


MODULE_PATH = Path(__file__).parents[1] / "fake-news-ingestion-service.py"
SPEC = importlib.util.spec_from_file_location("fake_news_ingestion_service", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
service = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(service)


class FakeChannel:
    def __init__(self) -> None:
        self.exchanges: list[dict] = []
        self.queues: list[dict] = []
        self.bindings: list[dict] = []
        self.published: list[dict] = []

    def exchange_declare(self, **kwargs) -> None:
        self.exchanges.append(kwargs)

    def queue_declare(self, **kwargs) -> None:
        self.queues.append(kwargs)

    def queue_bind(self, **kwargs) -> None:
        self.bindings.append(kwargs)

    def basic_publish(self, **kwargs) -> None:
        self.published.append(kwargs)


class FakeNewsIngestionServiceTest(unittest.TestCase):
    def test_declares_the_production_financial_news_topology(self) -> None:
        channel = FakeChannel()

        service.declare_topology(channel)

        self.assertEqual(
            [{"exchange": "financial-news-exchange", "exchange_type": "direct", "durable": True}],
            channel.exchanges,
        )
        self.assertEqual([{"queue": "financial-news", "durable": True}], channel.queues)
        self.assertEqual(
            [
                {
                    "exchange": "financial-news-exchange",
                    "queue": "financial-news",
                    "routing_key": "financial-news",
                }
            ],
            channel.bindings,
        )


    def test_startup_publisher_emits_only_one_fixed_headline(self) -> None:
        channel = FakeChannel()
        publisher = service.StartupHeadlinePublisher()

        with patch.object(
            service,
            "pika",
            SimpleNamespace(BasicProperties=lambda **kwargs: kwargs),
        ):
            self.assertTrue(publisher.publish_once(channel))
            self.assertFalse(publisher.publish_once(channel))

        self.assertEqual(1, len(channel.published))
        message = channel.published[0]
        self.assertEqual("financial-news-exchange", message["exchange"])
        self.assertEqual("financial-news", message["routing_key"])
        self.assertEqual(service.FIXED_HEADLINE, json.loads(message["body"]))
        self.assertEqual(
            {"content_type": "application/json", "delivery_mode": 2},
            message["properties"],
        )
