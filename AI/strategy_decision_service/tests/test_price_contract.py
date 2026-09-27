"""Unit tests for the price-prediction/Strategy contract."""

from __future__ import annotations

import importlib.util
import sys
import types
from pathlib import Path
import unittest


# Keep the decision rules testable on hosts that have not installed the runtime
# RabbitMQ/HTTP dependencies. They are only used when main() is run.
httpx_stub = types.ModuleType("httpx")
httpx_stub.HTTPError = Exception
pika_stub = types.ModuleType("pika")
sys.modules.setdefault("httpx", httpx_stub)
sys.modules.setdefault("pika", pika_stub)

MODULE_PATH = Path(__file__).parents[1] / "main.py"
SPEC = importlib.util.spec_from_file_location("strategy_decision_service", MODULE_PATH)
assert SPEC and SPEC.loader
strategy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(strategy)


PORTFOLIO = {
    "account": {"cash": 100_000},
    "positions": [{"symbol": "AAPL", "quantity": 7}],
}


class StrategyPriceContractTest(unittest.TestCase):
    def setUp(self) -> None:
        strategy.latest_prices.clear()

    def test_market_prediction_uses_its_latest_price_for_sizing_and_limit(self) -> None:
        decisions = strategy.evaluate_trading_decisions(
            {
                "ticker": "aapl",
                "direction": "UP",
                "confidence": 0.9,
                "latestPrice": 100.0,
                "triggeredBy": "market_tick",
            },
            PORTFOLIO,
        )

        self.assertEqual(
            decisions,
            [{
                "ticker": "AAPL", "action": "BUY", "quantity": 50,
                "order_type": "LIMIT", "limit_price": 100.1,
                "referencePrice": 100.0,
                "reason": "LSTM UP prediction with 90.0% confidence",
            }],
        )
        self.assertEqual(strategy.latest_prices, {"AAPL": 100.0})

    def test_news_prediction_without_price_uses_symbol_cache(self) -> None:
        # A low-confidence market prediction still refreshes the cache.
        self.assertEqual(strategy.evaluate_trading_decisions(
            {
                "ticker": "AAPL", "direction": "UP", "confidence": 0.6,
                "latestPrice": 100.0, "triggeredBy": "market_tick",
            },
            PORTFOLIO,
        ), [])

        decisions = strategy.evaluate_trading_decisions(
            {
                "ticker": "AAPL", "direction": "DOWN", "confidence": 0.9,
                "triggeredBy": "news_sentiment",
            },
            PORTFOLIO,
        )

        self.assertEqual(decisions[0]["action"], "SELL")
        self.assertEqual(decisions[0]["quantity"], 7)
        self.assertEqual(decisions[0]["limit_price"], 99.9)
        self.assertEqual(decisions[0]["referencePrice"], 100.0)

    def test_market_prediction_without_price_does_not_use_cache(self) -> None:
        strategy.latest_prices["AAPL"] = 100.0
        self.assertEqual(strategy.evaluate_trading_decisions(
            {
                "ticker": "AAPL", "direction": "UP", "confidence": 0.9,
                "triggeredBy": "market_tick",
            },
            PORTFOLIO,
        ), [])

    def test_invalid_price_is_held_and_not_cached(self) -> None:
        self.assertEqual(strategy.evaluate_trading_decisions(
            {
                "ticker": "AAPL", "direction": "UP", "confidence": 0.9,
                "latestPrice": 0, "triggeredBy": "market_tick",
            },
            PORTFOLIO,
        ), [])
        self.assertEqual(strategy.latest_prices, {})


if __name__ == "__main__":
    unittest.main()
