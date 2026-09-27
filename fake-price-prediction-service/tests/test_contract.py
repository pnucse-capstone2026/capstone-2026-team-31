"""Contract tests requiring neither RabbitMQ nor ML dependencies."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import unittest


MODULE_PATH = Path(__file__).parents[1] / "fake-price-prediction-service.py"
SPEC = importlib.util.spec_from_file_location("fake_price_prediction_service", MODULE_PATH)
assert SPEC and SPEC.loader
service = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(service)


class FakeChannel:
    def __init__(self) -> None:
        self.published: list[dict[str, object]] = []

    def basic_publish(self, **kwargs: object) -> None:
        self.published.append(kwargs)


def valid_bar() -> dict[str, float]:
    return {
        "Open": 180.0,
        "High": 182.0,
        "Low": 179.0,
        "Close": 181.0,
        "Volume": 50_000_000.0,
        "Return": 0.01,
        "MA_5": 179.5,
        "MA_20": 175.2,
        "Volatility_5": 0.02,
        "RSI_14": 61.5,
        "MACD": 1.2,
    }


class PricePredictionContractTest(unittest.TestCase):
    def setUp(self) -> None:
        service.price_buffer.clear()
        service.latest_news.clear()
        service.PREDICTION_OVERRIDES.clear()

    def test_first_market_bar_replaces_seed_history_and_publishes(self) -> None:
        channel = FakeChannel()
        bar = valid_bar()
        tick = json.dumps({"ticker": "AAPL", "bar": bar}).encode()

        service.on_market_tick(channel, None, None, tick)
        self.assertEqual(len(channel.published), 1)
        self.assertEqual(len(service.price_buffer["AAPL"]), service.SEQUENCE_LENGTH)
        self.assertEqual(service.price_buffer["AAPL"][-1], bar)
        published = channel.published[0]
        self.assertEqual(published["exchange"], service.RABBITMQ_PREDICTIONS_EXCHANGE)
        self.assertEqual(published["routing_key"], service.RABBITMQ_PREDICTIONS_QUEUE)
        payload = json.loads(published["body"])
        self.assertEqual(
            set(payload),
            {"predictionId", "ticker", "direction", "probabilityUp", "confidence", "latestPrice", "triggeredBy", "timestamp", "modelVersion"},
        )
        self.assertTrue(payload["predictionId"])
        self.assertEqual(payload["ticker"], "AAPL")
        self.assertEqual(payload["latestPrice"], 181.0)
        self.assertEqual(payload["triggeredBy"], "market_tick")
        self.assertIn(payload["direction"], {"UP", "DOWN"})
        self.assertGreaterEqual(payload["probabilityUp"], 0.0)
        self.assertLessEqual(payload["probabilityUp"], 1.0)
        self.assertGreaterEqual(payload["confidence"], 0.5)
        self.assertLessEqual(payload["confidence"], 1.0)
        if payload["direction"] == "UP":
            self.assertGreaterEqual(payload["probabilityUp"], 0.5)
        else:
            self.assertLess(payload["probabilityUp"], 0.5)

    def test_news_for_a_new_ticker_seeds_history_and_publishes(self) -> None:
        channel = FakeChannel()
        news = json.dumps({"ticker": "MSFT", "label": 1, "confidence": 0.813}).encode()
        service.on_news_sentiment(channel, None, None, news)
        self.assertEqual(len(channel.published), 1)
        self.assertEqual(len(service.price_buffer["MSFT"]), service.SEQUENCE_LENGTH)
        payload = json.loads(channel.published[0]["body"])
        self.assertEqual(payload["latestPrice"], service.MOCK_INITIAL_PRICE + 1.9)
        self.assertEqual(payload["triggeredBy"], "news_sentiment")

    def test_twenty_live_bars_replace_all_synthetic_history(self) -> None:
        channel = FakeChannel()
        bars = []
        for index in range(service.SEQUENCE_LENGTH):
            bar = valid_bar()
            bar["Close"] = 200.0 + index
            bars.append(bar)
            service.on_market_tick(
                channel,
                None,
                None,
                json.dumps({"ticker": "NVDA", "bar": bar}).encode(),
            )

        self.assertEqual(list(service.price_buffer["NVDA"]), bars)

    def test_ticker_override_makes_prediction_deterministic(self) -> None:
        service.PREDICTION_OVERRIDES["BUYOK"] = 0.9
        channel = FakeChannel()

        service.on_news_sentiment(
            channel,
            None,
            None,
            json.dumps({"ticker": "buyok", "label": 1, "confidence": 0.8}).encode(),
        )

        payload = json.loads(channel.published[0]["body"])
        self.assertEqual(payload["ticker"], "BUYOK")
        self.assertEqual(payload["direction"], "UP")
        self.assertEqual(payload["probabilityUp"], 0.9)
        self.assertEqual(payload["confidence"], 0.9)


if __name__ == "__main__":
    unittest.main()
