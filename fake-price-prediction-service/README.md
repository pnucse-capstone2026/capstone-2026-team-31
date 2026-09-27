# Fake Price Prediction Service

A lightweight RabbitMQ-compatible replacement for `AI/price_prediction_service`.
It preserves the production worker's message contracts and event-processing
logic, but replaces TensorFlow/scaler inference with a random probability from
`0.0` through `1.0`.

## Contracts and behavior

The mock consumes the same two JSON payloads from the same queues and
direct-exchange bindings:

```json
{"ticker":"AAPL","bar":{"Open":180.0,"High":182.0,"Low":179.0,"Close":181.0,"Volume":50000000,"Return":0.01,"MA_5":179.5,"MA_20":175.2,"Volatility_5":0.02,"RSI_14":61.5,"MACD":1.2}}
```

```json
{"ticker":"AAPL","label":1,"confidence":0.813}
```

The mock starts with a 20-bar synthetic window for `MOCK_INITIAL_TICKER`
(default `AAPL`). Each newly observed ticker is also seeded with 20 valid bars
before its first market or news event is processed, so it can publish
immediately. The seeded data is not permanent: every live market bar is
appended to the normal bounded 20-item deque and replaces the oldest synthetic
bar. After 20 live market bars, the window contains only RabbitMQ market data.
News events use the current window but do not change it. Set
`MOCK_INITIAL_PRICE` (default `100.0`) to control the synthetic history price.

For deterministic business-logic scenarios, set
`MOCK_PREDICTION_OVERRIDES` to a JSON ticker-to-probability map. For example,
`{"AAPL": 0.9, "MSFT": 0.1, "HOLD": 0.6}` produces respectively a confident
BUY signal, a confident SELL signal, and a HOLD signal under the default
strategy threshold. Symbols omitted from the map retain random predictions.

The testing manifest supplies deterministic single-symbol fixtures (`BUYOK`,
`BUYLOW`, `HOLD`, `SELLOK`, `SELLNONE`) and multi-symbol fixtures (`BUYA`,
`BUYB`, `LOWA`, `LOWB`, `HOLDA`, `HOLDB`, `SELLA`, `SELLB`, `SELLNONEA`, and
`SELLNONEB`).

The published body is sent to `RABBITMQ_PREDICTIONS_EXCHANGE` /
`RABBITMQ_PREDICTIONS_QUEUE` (default exchange / `predictions` by default):

```json
{"ticker":"AAPL","direction":"UP","probabilityUp":0.7314,"confidence":0.7314,"latestPrice":181.0,"triggeredBy":"market_tick","timestamp":1723000000.0}
```

`direction` and `confidence` use the production rules: `UP` for
`probabilityUp >= 0.5`, otherwise `DOWN`; confidence is the probability of the
chosen direction. `latestPrice` is the most recently registered valid `Close`
for the ticker; `triggeredBy` is `market_tick` or `news_sentiment`. It also
retains latest-news state, exponential sentiment decay, auto acknowledgement,
topology declaration, and malformed-message behavior. Set `MOCK_RANDOM_SEED`
for repeatable test output.

## Run

```bash
python3 -m pip install -r fake-price-prediction-service-requirements.txt
python3 fake-price-prediction-service.py
```

All RabbitMQ environment variables and defaults are identical to the
production consumer. Do not run the mock and production worker on the same
input queues, because RabbitMQ will distribute messages between them.
