# Approved-order contract

Order Execution consumes `orders.approved` messages for the installation's
single Alpaca paper account:

```json
{"ticker":"AAPL","action":"BUY","quantity":2,"order_type":"LIMIT","limit_price":180.18,"reason":"strategy decision"}
```

`connectionId`, user identifiers, and broker credential references are not
accepted. The service uses `ALPACA_API_KEY` and `ALPACA_API_SECRET` directly.
