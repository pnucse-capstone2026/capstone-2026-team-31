# Remaining Structural Blockers

These items prevent the currently implemented services from functioning as a
complete, account-aware paper-trading system. They are separate from the
accepted risks of secret exposure in logs and duplicate-order submission.

## 1. Strategy Decision and Account Service portfolio contract

Strategy Decision requests a simple portfolio response with `cash` and a
symbol-keyed `positions` map. Account Service exposes authenticated,
connection-specific portfolio endpoints with a different, nested projection
shape. Define one service-to-service endpoint and response contract, including
the authentication mechanism and selected connection/account.

## 2. Account-aware order routing

Approved orders do not carry a `connectionId`, Alpaca account ID, or broker
credential reference. Order Execution therefore cannot determine which user's
paper account should receive an order. Add an account/connection identifier to
the approved-order contract and resolve broker credentials through a controlled
Account Service boundary.

## 3. Order-event consumption and account projections

Order Execution publishes order events, but Account Service has no RabbitMQ
consumer that updates `order_projection`, `position_projection`, or portfolio
state. Implement an account-aware consumer and map broker order/trade events to
the existing projections.

## 4. Order-event identity and routing fields

The current Order Execution event omits the account/connection identity and a
reliable Alpaca order identifier needed for projection updates. Define a
versioned event that includes at least the connection/account ID, broker order
ID, client order ID, event ID, event timestamp, and event type.

## 5. Price Prediction feature completeness

Market Data now delivers the required `ticker`/`bar` envelope, but it currently
populates raw OHLCV values only. Price Prediction obtains its required feature
list from model metadata and may require derived values such as returns, moving
averages, volatility, RSI, or MACD. Make the model feature contract explicit,
calculate every required feature, and validate it before inference.

## 6. News batch processing

News Ingestion production code processes only the first item in a provider
WebSocket array. Later news items in the same provider frame can be dropped.
Process every array element and add an integration test covering multi-item
batches.

## 7. Reliable message processing

News Impact and Price Prediction use automatic RabbitMQ acknowledgement. A
processing error can acknowledge and lose a message instead of retrying or
dead-lettering it. Define retry/dead-letter behavior and use explicit
acknowledgement after successful processing.

## 8. Versioned shared event contracts

Service message DTOs are independently implemented. Establish versioned,
shared contracts (or a schema registry) for news, market bars, sentiment,
predictions, approved orders, and order events. Add compatibility tests for
producers and consumers.

## 9. Full controlled AI integration execution

The real AI workers depend on model artifacts downloaded from external model
repositories and were not executed as a fully local deterministic pipeline.
Provide test-safe, locally available model artifacts or a documented
contract-faithful test mode, then execute Market Data → Price Prediction →
Strategy Decision in the controlled environment.
