# Strategy Decision Service

Consumes predictions and publishes approved limit orders for the one configured
Alpaca account. It fetches `GET {ACCOUNT_SERVICE_BASE_URL}/internal/v1/portfolio`
without user or service-account authentication. Each approved order has no
per-account routing field; Order Execution always routes it with the installation-wide
`ALPACA_API_KEY` and `ALPACA_API_SECRET`.

RabbitMQ configuration remains environment based. Run with `python main.py`
after setting `ACCOUNT_SERVICE_BASE_URL` and RabbitMQ variables.

## Prediction input and approved-order output

Strategy consumes prediction messages with a valid positive finite
`latestPrice` and a `triggeredBy` value:

```json
{"predictionId":"a2a4d0a8-4af5-4f1d-87f6-5b3bc47d19f1","ticker":"AAPL","direction":"UP","probabilityUp":0.7314,"confidence":0.7314,"latestPrice":181.0,"triggeredBy":"market_tick","timestamp":"2026-09-04T00:00:00Z","modelVersion":"ef240954a295fc87de033ca853be215b567d53f8"}
```

It caches each valid `latestPrice` by ticker before applying the confidence
filter. A `news_sentiment` prediction from an older producer that lacks
`latestPrice` may use that ticker's cached value; a market-tick prediction that
lacks or supplies an invalid price is held. Price Prediction emits
`latestPrice` for every current prediction, so cache fallback is transitional
protection rather than the normal path.

Approved limit orders now include `referencePrice`, the price Strategy used to
size the order and calculate `limit_price`. This makes the decision auditable;
Order Execution's inbound DTO ignores this additional field safely.
