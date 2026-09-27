# Fake Alpaca API test controls

The fake broker keeps all state in memory. Its in-cluster test-control routes
are available under `/__mock`:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/__mock/state` | Read account, orders, positions, and watchlists. |
| POST | `/__mock/reset` | Restore default cash and clear all state. |
| POST | `/__mock/seed` | Replace cash, positions, orders, and watchlists with a scenario baseline. |
| POST | `/__mock/prices` | Set deterministic per-symbol prices. |
| POST | `/__mock/fill/{orderId}` | Fill a manually-filled order. |

`POST /__mock/seed` accepts a body like:

```json
{
  "cash": "100000",
  "positions": [
    {"symbol": "AAPL", "qty": "10", "avg_entry_price": "100"}
  ]
}
```

Positive quantities are long positions; negative quantities are short
positions. This test-only operation clears previous orders, positions, and
watchlists before applying the supplied baseline.
