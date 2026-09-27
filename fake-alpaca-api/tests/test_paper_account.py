from __future__ import annotations

import importlib.util
from decimal import Decimal
from pathlib import Path
import sys
import unittest


MODULE_PATH = Path(__file__).parents[1] / "fake-alpaca-api.py"
SPEC = importlib.util.spec_from_file_location("fake_alpaca_api", MODULE_PATH)
assert SPEC and SPEC.loader
fake_alpaca = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = fake_alpaca
SPEC.loader.exec_module(fake_alpaca)


class PaperAccountSeedTest(unittest.TestCase):
    def test_seed_replaces_cash_and_positions(self) -> None:
        account = fake_alpaca.PaperAccount("immediate", {"AAPL": Decimal("100")})
        account.create_order({"symbol": "AAPL", "qty": "1", "side": "buy", "type": "market"})

        account.seed("500", [{"symbol": "AAPL", "qty": "3", "avg_entry_price": "90"}])

        self.assertEqual(account.account()["cash"], "500")
        self.assertEqual(account.list_orders({"status": ["all"]}), [])
        self.assertEqual(account.position_list()[0]["symbol"], "AAPL")
        self.assertEqual(account.position_list()[0]["qty"], "3")

    def test_seed_accepts_short_positions(self) -> None:
        account = fake_alpaca.PaperAccount("immediate", {"MSFT": Decimal("100")})

        account.seed("500", [{"symbol": "MSFT", "qty": "-2", "avg_entry_price": "100"}])

        position = account.position_list()[0]
        self.assertEqual(position["side"], "short")
        self.assertEqual(position["qty"], "2")

    def test_seed_accepts_zero_cash(self) -> None:
        account = fake_alpaca.PaperAccount("immediate", {})

        account.seed("0", [])

        self.assertEqual(account.account()["cash"], "0")


if __name__ == "__main__":
    unittest.main()
