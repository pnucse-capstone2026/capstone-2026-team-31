# Market Data Service Unit Test Report

## Execution result

- Command: `./mvnw test`
- Result: **30 run, 30 passed, 0 failed, 0 errors, 0 skipped**
- External dependencies: none. WebSocket sessions, connection managers, schedulers, clocks, RabbitMQ templates, and publishers were mocked or faked. The former Spring context test was converted to an isolated test because the context auto-started a real configured WebSocket connection.

## Implementation inspected

The service is a narrow bar-ingestion pipeline:

1. `WebSocketClientConfig` creates a `WebSocketConnectionManager`, puts the configured Alpaca key/secret in HTTP headers, and enables auto-start.
2. `AlpacaWebSocketHandler.afterConnectionEstablished` notifies the reconnect supervisor and sends the configured subscription JSON verbatim.
3. The handler deserializes an incoming text frame as `AlpacaMessage[]`, reads element zero only, and recognizes only `T: "b"` (bar) and `T: "error"`. Bars are mapped to `MarketData` and published to exchange `market-data-exchange` with routing key `market-data`.
4. `MarketDataPublisher` delegates directly to `RabbitTemplate.convertAndSend`.
5. `AlpacaWebSocketReconnectSupervisor` schedules a reconnect after five seconds, verifies an asynchronous attempt after 15 seconds, uses capped exponential retry (5, 10, 20, 40, 60 seconds), and cancels scheduled work at shutdown.

Configured feed/channel behavior is entirely driven by `alpaca.api.market-stream-url` and the raw `alpaca.api.subscription-message`. The checked-in configuration subscribes to `bars:["*"]` on the IEX stream. There is no production symbol list, asset-class model, feed-name model, or channel validator.

## Added test coverage

| Production area | Test file | Covered observable behavior |
| --- | --- | --- |
| WebSocket handler | `src/test/java/com/ai_got_this/market_data_service/config/AlpacaWebSocketHandlerTest.java` | subscription send, close/transport forwarding, bar normalization/routing, provider errors and unknown status messages, malformed payload containment, publisher failure containment, batch behavior, unvalidated values |
| Reconnection | `src/test/java/com/ai_got_this/market_data_service/config/AlpacaWebSocketReconnectSupervisorTest.java` | initial delay, duplicate callbacks, manager restart, async timeout, exponential backoff, success reset, immediate success, shutdown idempotency, absent manager |
| Configuration and broker topology | `src/test/java/com/ai_got_this/market_data_service/config/MarketDataConfigurationTest.java` | durable queue, direct exchange, binding, converter, client headers/auto-start without connecting, observable lack of configuration validation, scheduler/clock beans |
| Publisher boundary | `src/test/java/com/ai_got_this/market_data_service/service/MarketDataPublisherTest.java` | exact RabbitTemplate call and exception propagation at the publisher boundary |
| Application test | `src/test/java/com/ai_got_this/market_data_service/MarketDataServiceApplicationTests.java` | isolated entry-point availability; no external bootstrap |

## Passing tests

All tests above passed. The suite verifies the realistic, high-risk scenarios identifiable from the current implementation and its messaging/WebSocket boundaries; it does not claim to test features that do not exist in this service.

## Failing tests

None.

## Tests that could not execute

None. A first local test attempt had test-source compilation/stubbing mistakes; these were corrected before the recorded run. They were not production failures.

## Risky and ambiguous behavior discovered

These are source-inspection findings and, where feasible, are captured as tests of current observable behavior—not failing tests that invent a contract.

1. **Resolved — provider batches are processed in order.** `AlpacaWebSocketHandler.handleTextMessage` iterates all decoded frame elements, and a per-item failure is contained so later events in the same frame are still processed. This is covered by `everyBarInProviderBatchIsProcessedInOrder` and `failurePublishingOneBarDoesNotPreventLaterBarsInTheSameFrame`.

2. **High — no bar or symbol validation; invalid market data is published.** The same handler maps OHLC/volume directly at [AlpacaWebSocketHandler.java](src/main/java/com/ai_got_this/market_data_service/config/AlpacaWebSocketHandler.java:71). Negative prices/volumes, invalid OHLC relationships, whitespace/lowercase symbols, null fields, and non-finite doubles are not rejected or normalized. This can create inaccurate prices and trading signals. Direction: validate the provider schema and domain invariants before publishing, and explicitly define normalization/rejection policy. Product-owner confirmation is needed for exact validation rules.

3. **High — timestamp, trade count, and any event identity are discarded.** `AlpacaBarMessage` has `t` and `n`, but `MarketData` construction omits them at [AlpacaWebSocketHandler.java](src/main/java/com/ai_got_this/market_data_service/config/AlpacaWebSocketHandler.java:71). No deduplication, ordering, stale-event filtering, correlation metadata, or event ID exists. Direction: extend the event contract only after product-owner agreement, then add deterministic dedupe/order tests.

4. **High — broker failures are swallowed by the WebSocket callback.** `handleTextMessage` catches all exceptions and logs only the message at [AlpacaWebSocketHandler.java](src/main/java/com/ai_got_this/market_data_service/config/AlpacaWebSocketHandler.java:83). The publisher itself correctly throws at [MarketDataPublisher.java](src/main/java/com/ai_got_this/market_data_service/service/MarketDataPublisher.java:16), but the caller suppresses the failure. This avoids a callback crash but gives no retry, dead-lettering, metric, or confirmed-delivery signal; market data may be lost silently. Direction: define delivery guarantees and introduce retry/confirm/dead-letter handling with redacted structured error logging.

5. **Resolved — basic WebSocket configuration is validated before client creation.** The client now rejects blank credentials/URL/subscription, non-`ws`/`wss` URLs, malformed subscription JSON, and subscriptions that do not use `action: "subscribe"` with a `bars` array. Exact allowed symbols/feed names remain a product/provider-contract concern.

6. **Medium — quotes, trades, authentication success/rejection, subscription acknowledgements, partial failures, and heartbeats have no dedicated behavior.** The polymorphic schema recognizes bars and errors only at [AlpacaMessage.java](src/main/java/com/ai_got_this/market_data_service/dto/AlpacaMessage.java:17); other messages are unknown and ignored. This is consistent with the implementation but ambiguous as a product capability. Direction: decide which upstream event types are required and model/validate them explicitly.

7. **Low — observability is limited to logs.** No metrics, health state, connection state, dead-letter interface, or structured error callback is present. The handler does not log secrets in the tested paths, but malformed-input exception messages are logged verbatim. Direction: define monitoring and sensitive-payload redaction requirements.

## Not unit-testable with the current separable boundaries

- Actual provider authentication acceptance, partial subscription response semantics, heartbeat protocol behavior, and remote close semantics require a provider-contract test double or integration environment; no such abstraction exists in production.
- Actual RabbitMQ serialization, broker confirms, timeout behavior, dead-lettering, and resource release require an AMQP integration boundary; the publisher exposes only `RabbitTemplate.convertAndSend`.
- Concurrent callback safety cannot be meaningfully asserted because the handler contains no state or explicit concurrency contract. The reconnect supervisor's deterministic scheduling state is tested with a single-thread fake scheduler.

## Assumptions

- Tests treat the current raw subscription string as the subscription contract and do not infer an allowed symbol list or asset-class taxonomy.
- A bar is the only eligible market-data event under the current implementation.
- The test suite intentionally uses no real credentials, network endpoint, WebSocket provider, or broker.
