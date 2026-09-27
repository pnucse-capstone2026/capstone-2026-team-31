# Price Prediction Service

This event-driven microservice predicts future stock price direction using historical market data and the output from the News Impact Prediction Service.

## Model

- **Model:** Multi-input LSTM
- **Inputs:**
  - Last 20 days of market features
  - News Impact prediction from the FinBERT service
- **Performance:**
  - Accuracy: **70.1%**

## Run

From the project root:

```bash
pip install -r requirements.txt
```

Set RabbitMQ configuration as environment variables, then start the consumer:

```bash
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USER=guest
export RABBITMQ_PASS=guest
export RABBITMQ_VHOST=/
python consumer.py
```

All RabbitMQ settings are environment based. The defaults are:


| Variable | Default | Purpose |
| --- | --- | --- |
| `RABBITMQ_HOST` | `localhost` | Broker host |
| `RABBITMQ_PORT` | `5672` | Broker port |
| `RABBITMQ_USER` | `guest` | Broker user |
| `RABBITMQ_PASS` | `guest` | Broker password |
| `RABBITMQ_VHOST` | `/` | Broker virtual host |
| `RABBITMQ_MARKET_TICKS_QUEUE` | `market-data` | Market-bar input queue (matches Market Data Service) |
| `RABBITMQ_NEWS_SENTIMENT_QUEUE` | `news-sentiment` | News-sentiment input queue (matches News Impact Service) |
| `RABBITMQ_PREDICTIONS_QUEUE` | `predictions` | Prediction destination/routing key |
| `RABBITMQ_PREDICTIONS_EXCHANGE` | `predictions-exchange` | Direct exchange that keeps the existing `predictions` delivery and also permits a measurement copy |
| `RABBITMQ_QUEUE_DURABLE` | `true` | Whether declared queues are durable |
| `RABBITMQ_MARKET_DATA_EXCHANGE` | `market-data-exchange` | Direct exchange published by Market Data Service |
| `RABBITMQ_MARKET_DATA_ROUTING_KEY` | `market-data` | Market-data exchange binding key |
| `RABBITMQ_NEWS_SENTIMENT_EXCHANGE` | `news-sentiment-exchange` | Direct exchange published by News Impact Service |
| `RABBITMQ_NEWS_SENTIMENT_ROUTING_KEY` | `news-sentiment` | News-sentiment exchange binding key |

The worker pins the reviewed model artifact revision by default. Set
`PRICE_PREDICTION_MODEL_REVISION` only when deploying a model revision whose
`metadata.json` feature contract has been reviewed and implemented by Market
Data.

## Event flow

The consumer receives the following messages:

- `market-data`: `{ "ticker": "AAPL", "bar": { ...market features... } }`
- `news-sentiment`: `{ "ticker": "AAPL", "label": 1, "confidence": 0.813 }`

Price bars are retained per ticker in a fixed-size window of 20. Once the
window is full, every incoming bar discards the oldest bar and produces a new
prediction. A news-sentiment message also produces a new prediction when its
ticker already has a full price window. The resulting message is published to
the configured prediction destination:

```json
{
  "predictionId": "a2a4d0a8-4af5-4f1d-87f6-5b3bc47d19f1",
  "ticker": "AAPL",
  "direction": "UP",
  "probabilityUp": 0.9742,
  "confidence": 0.9742,
  "latestPrice": 181.25,
  "triggeredBy": "market_tick",
  "timestamp": "2026-09-04T00:00:00Z",
  "modelVersion": "ef240954a295fc87de033ca853be215b567d53f8"
}
```

`latestPrice` is the most recent valid `Close` registered for the ticker in
the 20-bar window. `triggeredBy` is either `market_tick` or `news_sentiment`,
so consumers can distinguish a new tick from a prediction recomputed after
news. Both fields form part of the prediction output contract.
`predictionId` is a UUID created for every prediction. It is additive, so the
existing Strategy consumer continues to receive and process the same decision
data, while measurement can deduplicate an immutable prediction. `modelVersion`
is the pinned model artifact revision.

Both input consumers use RabbitMQ automatic acknowledgement (`auto_ack=True`).
At startup, the worker declares and binds both input queues to the matching
direct exchanges. The publisher payload schemas must still match the documented
input shapes below.

## Role in the System

This service is responsible for the Price Prediction module of the 24-Hour Trading Bot. It receives recent market data and the prediction output from the News Impact Service, then returns the predicted stock direction and confidence for the trading decision pipeline.
