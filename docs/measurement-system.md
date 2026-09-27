# Passive paper-trading measurement

The measurement system has two independent Spring Boot services in the `trading-bot-measurement` namespace.

- `pnl-service` owns the `pnl` schema and only consumes `measurement.executions` and `measurement.market-ticks`.
- `direction-evaluation-service` owns the `direction_eval` schema and only consumes `measurement.predictions` and `measurement.market-ticks`.

They use separate databases on the existing PostgreSQL instance: `pnl_measurement` and `direction_evaluation_measurement`. Neither service connects to an existing service database, publishes an approved order, invokes Fake Alpaca, or writes outside its own database/schema. Both produce structured JSON stdout logs with a static `service` field.

## Passive RabbitMQ topology

The definitions in `RabbitMQ/rabbitmq.yml` are durable and use the existing `trading-bot-vhost`.

| Source exchange | Routing key | Existing destination retained | New measurement destination |
| --- | --- | --- | --- |
| `market-data-exchange` (direct) | `market-data` | `market-data` | `measurement.market-ticks` |
| `predictions-exchange` (direct) | `predictions` | `predictions` | `measurement.predictions` |
| `measurement-executions-exchange` (direct) | `measurement.executions` | n/a | `measurement.executions` |

The new market binding is a second queue binding, so it copies messages; neither measurement service consumes `market-data`. Predictions formerly used RabbitMQ's default exchange, which cannot fan out. Price Prediction now publishes to `predictions-exchange`; its `predictions` binding preserves Strategy's original delivery, and the measurement binding is a parallel copy. `predictionId` (UUID) and `modelVersion` are additive, so Strategy ignores them safely.

Order Execution still sends existing order events to `order-events-fanout`. Separately, it emits the normalized contract to `measurement-executions-exchange` only after a broker `fill` or `partial_fill` notification with a positive new quantity and price. `new`, rejected, cancelled, and zero-filled updates publish no measurement execution.

No measurement binding has a business queue as its source or consumer. This avoids RabbitMQ competing consumers entirely.

## Event contracts

`measurement.market-ticks` copies the Market Data message and adds deterministic source metadata:

```json
{"ticker":"AAPL","eventId":"...","timestamp":"2026-09-04T00:00:00Z","bar":{"Close":102.00}}
```

The ID derives from source symbol and bar timestamp. The price is exactly the `bar.Close` delivered to Price Prediction. For deterministic Fake Alpaca runs, configure Fake Alpaca's `--price` fixture to the same bar close. The current Fake Alpaca does not publish market bars; matching that fixture to the market-feed fixture is a deployment/test responsibility. Measurement never reads or mutates Fake Alpaca.

`measurement.predictions` includes `predictionId`, ticker, direction, `probabilityUp`, confidence, `latestPrice`, timestamp, `triggeredBy`, and `modelVersion`. `measurement.executions` contains only `executionId`, `brokerOrderId`, ticker, `BUY`/`SELL`, `newlyFilledQuantity`, fill price, timestamp, and order status.

## Measurements

Directional accuracy is a model label, not a trading outcome. At `predictionTimestamp + horizon` (default `PT1H`), Direction Evaluation takes the first valid market price at or after that time. It calculates `(evaluationPrice - latestPrice) / latestPrice`, labels the result `UP`, `DOWN`, or `FLAT`, and compares it with the model direction. Low-confidence UP/DOWN predictions remain evaluable even if Strategy would HOLD.

P&L uses execution fills only. FIFO long accounting opens a lot for every BUY and closes oldest lots first for every SELL. A matched lot's realized P&L is `(sellPrice - buyPrice) * quantity`. An oversell becomes an accounting anomaly; it does not create a short position or alter cash/lots. Derived cash starts at `100000.00` by default, falls on filled buys, and rises on filled sells. Unrealized P&L is `(latestMarketPrice - lotCost) * remainingQuantity`.

Example: buy 49 shares at `$100`, receive a market price of `$102`, then sell 49 at `$102`. Before selling, unrealized P&L is `$98`; after selling, realized P&L is `$98` and no lot remains open.

Unique database keys plus transactions make execution IDs, prediction IDs, and market event IDs idempotent. `pnl-service` emits one structured JSON `WARN` when cash crosses from positive to zero/below, not repeatedly while already non-positive.

Read-only endpoints:

- `GET /api/v1/pnl`
- `GET /api/v1/direction-evaluations/summary`
- `GET /api/v1/direction-evaluations/by-ticker`
- `GET /api/v1/direction-evaluations/by-confidence-band`
- `GET /api/v1/direction-evaluations/pending`

## Configuration and later deployment

Common settings are `MEASUREMENT_DATABASE_URL`, `MEASUREMENT_DATABASE_USERNAME`, `MEASUREMENT_DATABASE_PASSWORD`, `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`, and `SPRING_RABBITMQ_VIRTUAL_HOST`. P&L additionally uses `MEASUREMENT_PNL_ACCOUNT_ID` and `MEASUREMENT_PNL_STARTING_BALANCE`; Direction Evaluation uses `MEASUREMENT_DIRECTION_EVALUATION_HORIZON` (default `PT1H`). Queue overrides are `MEASUREMENT_MARKET_TICKS_QUEUE`, `MEASUREMENT_EXECUTIONS_QUEUE`, and `MEASUREMENT_PREDICTIONS_QUEUE`.

`PostgreSQL/measurement-namespace.yml`, `PostgreSQL/postgresql.yml`, `pnl-service/pnl-service.yml`, and `direction-evaluation-service/direction-evaluation-service.yml` are intentionally not applied. The existing PostgreSQL init ConfigMap creates `pnl_measurement` and `direction_evaluation_measurement`; because the official PostgreSQL init directory runs only when the data directory is first initialized, add those databases manually if the existing cluster has already been initialized. Before reviewed deployment, create these namespace-local secrets through the secret manager:

- `measurement-postgresql-credentials`: `POSTGRES_USER` and `POSTGRES_PASSWORD` for an existing PostgreSQL role that can access both measurement databases. Kubernetes Secrets cannot be referenced across namespaces, so this is a namespace-local copy managed by the secret manager.
- `measurement-rabbitmq-credentials`: `username`, `password`, and `vhost`, with read permission only for the three dedicated measurement queues.

The manifests connect to `postgresql.trading-bot-testing.svc.cluster.local` and assume the current RabbitMQ namespace is `trading-bot-testing`; override either host if it is elsewhere. Build reviewed jars/images, then apply the RabbitMQ definition update before measurement workloads. This change does not push images or apply any manifest.

## Local unit tests

```sh
(cd pnl-service && mvn test)
(cd direction-evaluation-service && ./mvnw test)
python3 -m unittest RabbitMQ.tests.test_measurement_topology
```

These are unit-only tests. There are no Testcontainers, real RabbitMQ/PostgreSQL, Docker Compose, Kubernetes, or Fake Alpaca calls.
