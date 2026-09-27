"""Unit-level validation of the imported RabbitMQ definitions; no broker is started."""

from __future__ import annotations

import json
import unittest
from pathlib import Path


class MeasurementTopologyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        manifest = Path(__file__).parents[1] / "rabbitmq.yml"
        lines = manifest.read_text().splitlines()
        start = lines.index("  definitions.json: |") + 1
        encoded = []
        for line in lines[start:]:
            if line == "---":
                break
            encoded.append(line.removeprefix("    "))
        cls.definitions = json.loads("\n".join(encoded))

    def test_measurement_queues_are_durable_and_are_not_business_queues(self) -> None:
        queues = {queue["name"]: queue for queue in self.definitions["queues"]}
        for name in ("measurement.market-ticks", "measurement.predictions", "measurement.executions"):
            self.assertTrue(queues[name]["durable"])
        self.assertIn("market-data", queues)
        self.assertIn("predictions", queues)

    def test_copies_are_bound_from_exchanges_without_consuming_business_queues(self) -> None:
        exchanges = {exchange["name"]: exchange for exchange in self.definitions["exchanges"]}
        for name in ("market-data-exchange", "predictions-exchange", "measurement-executions-exchange"):
            self.assertTrue(exchanges[name]["durable"])
        bindings = self.definitions["bindings"]
        expected = {
            ("market-data-exchange", "measurement.market-ticks", "market-data"),
            ("predictions-exchange", "measurement.predictions", "predictions"),
            ("measurement-executions-exchange", "measurement.executions", "measurement.executions"),
        }
        found = {(item["source"], item["destination"], item["routing_key"]) for item in bindings}
        self.assertTrue(expected.issubset(found))
        self.assertIn(("market-data-exchange", "market-data", "market-data"), found)
        self.assertIn(("predictions-exchange", "predictions", "predictions"), found)
        self.assertFalse(any(item["source"].startswith("measurement.") for item in bindings))


if __name__ == "__main__":
    unittest.main()
