# News Ingestion Service — unit-test report

## Scope and architecture inspected

The Spring Boot entry point starts component scanning. `WebSocketClientConfig` validates nonblank credentials, a `ws`/`wss` URL, and a JSON news-subscription message before constructing an auto-starting `WebSocketConnectionManager`. Invalid configuration logs an error and does not create a manager (`WebSocketClientConfig.java:22-90`).

`AlpacaWebSocketHandler` sends the configured subscription message after every connection (`AlpacaWebSocketHandler.java:31-48`).

Text callbacks deserialize the payload as `AlpacaMessage[]` and process every element, then:

* log authentication failures for provider error codes 401, 402, and 404, and log successful `authenticated` acknowledgements;
* for news, build one `NewsHeadline(ticket, headline)` per symbol and publish to the hard-coded `financial-news-exchange` / `financial-news` pair;
* log unrecognized message types; and
* catch and log every exception in the callback (`AlpacaWebSocketHandler.java:41-83`).

`NewsPublisher` delegates directly to `RabbitTemplate.convertAndSend` (`NewsPublisher.java:15-16`). `RabbitMqConfig` supplies the durable queue, direct exchange, binding, and Jackson message converter (`RabbitMqConfig.java:11-29`). Polymorphic type selection uses `T`, including `AlpacaSuccessMessage` for success acknowledgements and `AlpacaUnknownMessage` for unknown types (`AlpacaMessage.java:10-26`).

There is no implemented deduplication, timestamp/content/source mapping, filtering, ordering guarantee, broker-confirm handling, metrics, or explicit concurrency control beyond the reconnect supervisor's internal synchronization.

## Test plan and implemented coverage

| Production boundary | Tests | Observable behavior covered |
| --- | --- | --- |
| `AlpacaWebSocketHandler.afterConnectionEstablished` | subscription success and send-failure tests | exact subscription frame and transport failure propagation |
| `AlpacaWebSocketHandler.handleTextMessage` | valid, malformed parameterized, authentication acknowledgement/failure, error/unknown, empty-symbol, unknown-field, multi-element, duplicate, normalization, and publisher-failure tests | parsing, acknowledgement log levels, polymorphic routing, per-element failure isolation, Unicode, multi-symbol fan-out, exact publication contract, schema evolution, duplicate delivery, and follow-on callback recovery |
| `AlpacaMessage` DTO mapping | `AlpacaMessageDeserializationTest` | news and authentication-success mapping, Korean/emoji preservation, IDs/symbols, and nested unknown-message fields |
| `NewsPublisher` | `NewsPublisherTest` | exact exchange, routing key, and object delegation |
| `RabbitMqConfig` / `WebSocketClientConfig` | `MessagingAndWebSocketConfigurationTest` | durable queue, direct exchange/binding, JSON converter, valid headers/auto-start setting, and fail-safe invalid configuration |
| application bootstrap | `NewsIngestionServiceApplicationTests` | application class loads without external infrastructure |

The tests use in-memory recording fakes and a `WebSocketSession` interface proxy. No provider, WebSocket endpoint, credentials, RabbitMQ broker, clock, scheduler, or random source is contacted. The previous context-load test was changed because its auto-starting production connection manager attempts a real external WebSocket connection.

## Execution result

Command: `./mvnw test`

Final result: **38 run, 38 passed, 0 failed, 0 skipped**.

### Passed

All final tests listed above passed. The test suite is deterministic and completes in a few seconds.

### Failed during test development (resolved expectation, not a production defect)

`AlpacaWebSocketHandlerTest.unknownFieldsOnNewsCauseTheEntireMessageToBeRejectedByCurrentMapperSettings` at the former line 80 initially expected an unknown news field to reject publication. It failed because the configured mapper ignored the extra field and published the known fields. The test was renamed to `unknownFieldsOnNewsAreIgnoredAndKnownFieldsArePublished` and changed to assert this observed contract; this was a correction of an unsupported test assumption, not an assertion weakening.

* Scenario / expected: unknown fields were assumed to be rejected.
* Actual: one `AAPL` headline publication occurred.
* Implicated code: `AlpacaWebSocketHandler.handleTextMessage`, `AlpacaWebSocketHandler.java:40-58`.
* Severity: Low.
* Production impact: provider schema additions are currently tolerated, which is generally resilient, but silently ignored fields may hide a contract change.
* Proposed direction: decide whether strict schema validation or forward-compatible ignoring is desired, then make the policy explicit and observable.
* Expectation origin: product assumption, not an existing contract.

An earlier repeat run also could not initialize Mockito's dynamic JVM agent on this JDK. The suite was refactored to use local fakes instead; the final command has no Mockito dependency and passed.

### Could not be executed / intentionally not unit-tested

* Real connection and provider acknowledgement exchange, broker serialization on the wire, broker timeouts, and publisher confirms: intentionally excluded because the request prohibits external endpoints/brokers and production exposes no injectable transport/broker-confirm seam. Acknowledgement parsing and log behavior are unit-tested.
* Real provider disconnect/reconnect, retry exhaustion against a provider endpoint, and broker serialization on the wire remain integration concerns. Reconnect scheduling, close/error callbacks, lifecycle cancellation, and retries are unit-tested with deterministic fakes.
* Binary frames: the implementation only overrides `handleTextMessage`; no binary-frame behavior is defined.
* Clock-based stale/future timestamp validation, source/content mapping, correlation IDs, payload-size limits, invalid encoding, and ordering/concurrency guarantees: those fields or policies do not exist in the current DTO/event model.

## Production risks and ambiguities

| Severity | Evidence | Why it matters | Proposed implementation direction | Contract basis |
| --- | --- | --- | --- | --- |
| Critical | credentials are present in `application.properties:3-13` | secrets in repository configuration can be leaked through source access or builds | rotate exposed credentials and use secret injection | existing configuration, no product assumption |
| High | `AlpacaNewsMessage.id` is unused and each callback publishes directly (`AlpacaWebSocketHandler.java:73-82`) | duplicate delivery produces duplicate broker messages; at-least-once provider delivery can amplify downstream decisions | define an idempotency key/store and broker-publish strategy | existing code behavior; whether dedupe is required needs owner confirmation |
| Medium | broad `catch (Exception)` logs `e.getMessage()` only (`AlpacaWebSocketHandler.java:89-90`) | message loss has no retry/dead-letter/metric and debugging lacks exception context | classify parsing vs publish errors; add safe structured logs, metrics, and a durable failure policy | existing code behavior |
| Medium | symbols/headline are forwarded unchanged (`AlpacaWebSocketHandler.java:74-79`) | blank, whitespace, lowercase/invalid symbols and null headlines can reach consumers | define and validate/normalize the event schema before publishing | behavior is explicit; desired validation needs owner confirmation |
| Medium | hard-coded exchange/routing values in handler (`AlpacaWebSocketHandler.java:81`) | topology changes require a code deployment and can drift from configuration | move topology names to validated configuration/constants shared with broker config | existing code behavior |
| Medium | authentication failures caused by permanently invalid credentials will still retry after a connection closes | retries cannot restore invalid credentials and can create recurring operational noise | classify code 402 as non-retryable until configuration changes, with an explicit operational alert | product policy needed |

This suite covers the realistic, high-risk scenarios identifiable from the current implementation and its external boundaries; it does not claim to test every possible scenario.
