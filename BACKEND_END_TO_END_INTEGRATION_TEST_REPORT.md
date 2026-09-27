# Backend end-to-end integration test report

## Result

**Deployment readiness: NOT READY.** No production source, production configuration, message schema, migration, or infrastructure file was changed. The controlled runtime could not be started on 2026-08-18 because the local Docker daemon was unavailable; therefore the executable checks below were blocked before service startup. The repository map and static contract assessment are based on production source, not the existing unit-test reports.

## Test artifacts and command

Test-only artifacts added:

- `integration-tests/compose.yaml` — disposable official RabbitMQ 4.1 management and PostgreSQL 17 containers, each with fake-only credentials and health checks.
- `integration-tests/fake-alpaca/src/FakeAlpaca.java` — loopback-only REST/WebSocket/JWKS/OAuth test double. It records requests and subscription frames and never forwards traffic.
- `integration-tests/run_backend_integration.py` — bounded-polling runner that packages and starts the implemented Spring services with environment overrides, collects logs under `integration-tests/artifacts`, and tears down `docker compose down -v` even on failure.

Run from the repository root:

```sh
python3 integration-tests/run_backend_integration.py
```

Observed startup command/evidence:

```text
docker compose -f integration-tests/compose.yaml up -d --wait
unable to get image 'postgres:17-alpine': Cannot connect to the Docker daemon
at unix:///Users/luigi/.docker/run/docker.sock. Is the docker daemon running?
```

The runner records this as a **Test-environment defect** and performs cleanup. Start Docker Desktop/the Docker daemon, then rerun the one command above. This is an environment blocker, not an application failure.

## Controlled topology

```text
fake Alpaca REST/JWKS/OAuth : 127.0.0.1:18080
fake news / market / trade WebSockets : 127.0.0.1:18081 / :18082 / :18083
RabbitMQ (vhost `integration`) : localhost:5673, management :15673
PostgreSQL (`accounts_it`) : localhost:15432
Spring services (host processes) : account :8081, news :8082, market :8083, order :8084
```

All injected records carry a generated `it-<random>` run identifier where their current schema permits it. Polling is bounded (45 seconds; 250 ms diagnostic interval). No public URL, Alpaca account, real token, or real key is contacted. The fake provider accepts only local loopback traffic and has no proxying code.

## Implemented-system map

| Component | Actual runtime dependencies | Implemented boundary |
|---|---|---|
| News Ingestion | RabbitMQ; Alpaca news WS | `AlpacaWebSocketHandler` reads an array but processes index 0 only; publishes a `NewsHeadline(ticker, headline)` per symbol. |
| Market Data | RabbitMQ; Alpaca market WS | Reads every array entry; publishes `MarketTick(ticker, bar)`. |
| News Impact | RabbitMQ; downloaded Hugging Face model | FastAPI exposes only `GET /`; inference is an internal RabbitMQ callback, not an inference HTTP endpoint. |
| Price Prediction | RabbitMQ; TensorFlow and Hugging Face artifacts | Long-running AMQP worker only; no HTTP API. |
| Strategy Decision | RabbitMQ; configurable HTTP portfolio placeholder | Long-running AMQP worker only; no HTTP API. |
| Order Execution | RabbitMQ; Alpaca order REST and trade-update WS | AMQP listener validates positive-priced limit orders, invokes REST with its internal `day` time-in-force, and publishes WS trade updates as a fanout `OrderEvent`. |
| Account | PostgreSQL/Flyway; Keycloak resource-server JWT; Alpaca OAuth/REST | REST API, OAuth state, in-memory secret store, reconciliation, projections. No RabbitMQ listener is implemented. |

The Account migrations are `account-service/src/main/resources/db/migration/V1__accounts_schema.sql`; `ScheduledReconciliationRunner` uses the existing five-minute default schedule. It reconciles the broker account and watchlists, but not positions or orders. `ProcessedEvent`, `OutboxEvent`, and projection tables have no corresponding implemented messaging consumer/publisher in the inspected source.

## Producer-to-consumer matrix

| Producer | Exchange/queue or HTTP endpoint | Message/API contract | Consumer | Verified status |
|---|---|---|---|---|
| Fake Alpaca news WS | `ws://…/news` | Alpaca array with `T=n`, `symbols`, `headline` | News Ingestion | Runtime blocked; handler only uses first array item. |
| News Ingestion | `financial-news-exchange` / `financial-news` | `NewsHeadline`: `ticker`, `headline` | News Impact | Contract aligned. |
| News Impact | `news-sentiment-exchange` / `news-sentiment` | root `ticker`, `label`, `confidence` | Price Prediction | Queue, direct-exchange binding, and payload contract aligned. |
| Fake Alpaca market WS | `ws://…/market` | Alpaca array with `T=b`, `S`, OHLCV | Market Data | Runtime blocked. |
| Market Data | `market-data-exchange` / `market-data` | `MarketTick`: `ticker`, `bar` (OHLCV features) | Price Prediction | Queue, direct-exchange binding, and payload envelope aligned. |
| Price Prediction | default exchange / `predictions` | `ticker`, `direction`, `probabilityUp`, `confidence` | Strategy Decision | Configurable queue matches by default; payload compatible with its decision fields. Runtime blocked due model downloads. |
| Strategy Decision | default exchange / `orders.approved` | `ticker`, `action`, `quantity`, `order_type`, `limit_price` | Order Execution | Queue names align; Order Execution accepts these legacy names as JSON aliases and validates a positive limit price. |
| Fake Alpaca trade WS | `ws://…/trade` binary `trade_updates` | stream/data/order shape | Order Execution | Runtime blocked. |
| Order Execution | `order-events-fanout` | sparse `OrderEvent` (`event`, cancellation timestamps, client ID, updatedAt) | portfolio and notification queues only | No Account/Risk/Notification consumer in source; dead-end. |
| Account Service | `GET /api/v1/...` | Account REST projections | Frontend only (excluded) | No service-to-service portfolio endpoint matching Strategy default. |

## Failing findings / blocked scenarios

### Existing unit-suite secret disclosure failure

- **Test/source:** `doesNotExposeConfiguredCredentialsWhenTheBrokerClientFails`, `order-execution-service/src/test/java/com/ai_got_this/order_execution_service/service/AlpacaBrokerTest.java:105`.
- **Services:** Order Execution.
- **Scenario:** broker `RestTemplate` throws an error that includes the test-only secret.
- **Expected:** the secret must not reach logs.
- **Actual:** `./mvnw test -q` failed: `expected: <false> but was: <true>`; log evidence included `Detailed Explanation: [request rejected: test-only-secret]`.
- **Implicated source:** `order-execution-service/src/main/java/com/ai_got_this/order_execution_service/service/AlpacaBroker.java` catch block.
- **Classification:** **Critical production defect.**
- **Why it matters:** provider/client exceptions can carry credentials or authorization data into deployment logs.
- **Direction:** log a stable error category/status and a redacted correlation identifier; never log raw exception messages from provider transports.
- **Production fix applied:** none.

### Duplicate order delivery is not idempotent

- **Test/source:** `duplicate approved order is idempotent`, `integration-tests/run_backend_integration.py:45`.
- **Services:** RabbitMQ, Order Execution, fake Alpaca REST.
- **Precondition/scenario:** publish the same valid `orders.approved` payload twice and count `POST /v2/orders` records.
- **Expected:** one broker submission, or a persisted idempotency decision with a deterministic duplicate outcome.
- **Actual:** blocked by Docker; source inspection establishes that `ListenerService.listen` calls `broker.placeOrder` for every delivery and no idempotency key/store exists.
- **Implicated source:** `ListenerService.java`, `AlpacaBroker.java`; no persistence model in this service.
- **Classification:** **Critical production defect.**
- **Why it matters:** redelivery can create duplicate paper/live orders.
- **Direction:** add an immutable client-order/idempotency key, durable submission ledger/outbox, broker-side client order ID, and consumer deduplication before acknowledgement.
- **Production fix applied:** none.

### Structural contract failures and missing links

The concrete mismatches in the matrix are **Missing implementation/integration links** (high deployment risk): no Account consumer for order events. In addition, News Ingestion's `AlpacaWebSocketHandler.java` silently drops every batch item after index zero, a **High production risk** for provider batches. The Price Prediction queue-binding gap, the News/Market/News Impact payload contracts, and Strategy-to-Order compatibility were later fixed at the user's request; no other production fix was applied.

## Scenarios executed, passed, failed, and blocked

| Category | Result |
|---|---|
| Existing Java unit suites | News and Market passed; Account command exited successfully; Order Execution failed on secret disclosure (above). |
| Fake provider compilation | Passed with `javac`; Python runner syntax check passed. |
| Docker infra, service readiness, migrations | Blocked: Docker daemon unavailable. |
| News/market WS → RabbitMQ, reconnect/error/batch checks | Blocked before services start. |
| Real RabbitMQ Order Execution REST/WS checks | Blocked before services start. |
| Account JWT/OAuth/reconciliation/PostgreSQL direct assertions | Blocked before services start. |
| AI HTTP contract tests | Not executable by design: News Impact and Price Prediction implement no inference HTTP APIs; both require external model artifacts. Strategy is an AMQP worker with only a placeholder portfolio HTTP dependency. |

The runner implements the runnable readiness, subscription, basic news/market publication, order REST schema, duplicate-order, trade-update, migration/authentication checks. It will emit per-service logs and a machine-readable `integration-tests/artifacts/summary.json` after a Docker-enabled run. Additional malformed/reconnect/provider-rejection matrix scenarios remain not executed because the environment did not start; their outcomes must not be inferred.

## Limitations and release conclusion

This fixture currently tests only the provider behavior the source actually invokes, and does not pretend to supply model weights or missing production components. Account authentication uses a test-only signed local JWT/JWKS mechanism consistent with its configured resource-server model; it does not test Keycloak. The fake's recorded authentication data should be considered test evidence only and contains fake values.

Even after Docker is available, the documented duplicate submission, secret leakage, and contract/link gaps require remediation before a smooth deployment. **NOT READY** for controlled paper deployment.

## Runtime execution addendum — 2026-08-18

Docker Desktop was subsequently made available and the suite was executed with the dedicated test ports documented above. The runner first encountered an occupied host PostgreSQL port; the **test-only** Compose mapping was moved to `15432` (and RabbitMQ to `5673`/`15673`) without altering production configuration. Docker containers became healthy and teardown completed, including volume removal.

### Runtime pass evidence

| Scenario | Observed result | Evidence |
|---|---|---|
| Infrastructure readiness | Passed | Fake provider returned `{"status":"UP"}`; official RabbitMQ and PostgreSQL Compose health checks passed. |
| RabbitMQ management readiness | Passed | Management API became available with test-only Basic authentication. |
| News fake WebSocket → real RabbitMQ | Passed | A fake `T=n` frame for `AAPL`/`MSFT` produced at least two consumable messages on `financial-news`. News log records the real WS connection and RabbitMQ connection to `127.0.0.1:5673/integration`. |
| Market fake WebSocket → real RabbitMQ | Passed | A fake bar frame produced a consumable message on `market-data`. Market log records the real WS connection and RabbitMQ connection to `127.0.0.1:5673/integration`. |
| Streaming-service shutdown | Passed | Both logs record WebSocket manager stop and Tomcat graceful shutdown completion. |

### Runtime failures and blocks

#### Account Service cannot apply Flyway migration to PostgreSQL

- **Test/source:** `account migration and authenticated readiness`, `integration-tests/run_backend_integration.py:42` (line may shift as the runner evolves).
- **Services:** Account Service, PostgreSQL 17.11, Flyway.
- **Expected:** Account Service starts, Flyway applies `V1__accounts_schema.sql`, then the test-only signed JWT can call `GET /api/v1/me`.
- **Actual:** startup terminated before migration: `Unsupported Database: PostgreSQL 17.11` from `flyway-core-11.7.2`; the HTTP readiness call was refused.
- **Implicated production component:** `account-service/pom.xml` declares `flyway-core` but no PostgreSQL Flyway database-support module; `account-service/src/main/resources/application.properties` enables Flyway.
- **Classification:** **Critical production defect.**
- **Why it matters:** the Account Service cannot provision its PostgreSQL schema in the controlled deployment environment, blocking persistence, reconciliation, and all authenticated account APIs.
- **Direction:** include the Flyway PostgreSQL database-support artifact version-aligned with Spring Boot/Flyway, then run the migration against the supported PostgreSQL version in CI.
- **Production fix applied:** none.

#### Order Execution refuses the controlled fake provider before startup

- **Test/source:** `approved order reaches fake broker`, `integration-tests/run_backend_integration.py:45`.
- **Services:** Order Execution, RabbitMQ, fake Alpaca REST/WebSocket.
- **Expected:** service accepts a test-only local provider endpoint and reaches its configured queue/REST boundary.
- **Actual:** startup aborts with `Invalid application configuration: alpaca.api.order-endpoint must be a valid https URL` when pointed at `http://127.0.0.1:18080/v2/orders`. Consequently no order listener, trade-update publisher, or duplicate-delivery runtime check could execute.
- **Implicated production component:** `order-execution-service/src/main/java/com/ai_got_this/order_execution_service/config/ApplicationConfigurationValidator.java`.
- **Classification:** **Test-environment limitation with deployment-testability risk.** The HTTPS requirement is appropriate for production but the current source has no configured trust-store/test profile path for a local TLS fake.
- **Why it matters:** the service cannot be validated against a controlled provider substitute, and TLS/certificate behavior remains untested before deployment.
- **Direction:** add a test-only TLS fixture/trust-store configuration (without weakening production HTTPS validation) and test against a loopback TLS fake provider.
- **Production fix applied:** none.

#### Fake-provider subscription observation is incomplete

`stream subscriptions` failed because the compact test-only WebSocket recorder did not persist the subscription frame before the assertion deadline, even though both Spring logs show subscription sends and successful WS connections. This is a **Test-environment defect** in `integration-tests/fake-alpaca/src/FakeAlpaca.java`, not evidence of a production failure. The passed News/Market delivery tests independently prove the active WS connections and RabbitMQ publishing paths.

#### Order-event observation is blocked

The order-event queue lookup returned HTTP 404 because Order Execution never started (HTTPS validation failure above), so its fanout exchange/queues were never declared. This is not an assertion about trade-update conversion behavior.

### Corrected run summary

| Category | Passed | Failed / blocked |
|---|---:|---:|
| Infrastructure and service lifecycle | 3 | 0 |
| News flow | 1 | batch/malformed/reconnect scenarios not yet executed |
| Market flow | 1 | malformed/reconnect scenarios not yet executed |
| Account/PostgreSQL | 0 | 1 critical startup/migration defect |
| Order Execution | 0 | 1 provider-TLS testability block; duplicate safety remains a source-confirmed critical risk |
| AI services | 0 | no implemented inference HTTP APIs; model artifacts require external downloads |

This runtime addendum supersedes the earlier Docker-daemon blocker. The deployment conclusion remains **NOT READY**.

## Latest controlled rerun — 2026-08-18

This rerun used the corrected RabbitMQ bindings and payload contracts, a PostgreSQL 17 container on port `15432`, and a test-only predeclared `orders.approved` ingress queue. It supersedes the earlier Account/Flyway startup observation: Account Service now started successfully and Flyway applied V1 to PostgreSQL 17.11.

### Passed runtime scenarios

1. Infrastructure and RabbitMQ management readiness.
2. News Ingestion fake WebSocket to real RabbitMQ publication.
3. Market Data fake WebSocket to real RabbitMQ publication.
4. Streaming/WebSocket subscription observation.
5. Order Execution startup and `orders.approved` consumption.
6. Valid limit order submission to the fake broker.
7. Exact broker request contract assertion (including the internally supplied `time_in_force: day`).
8. Fake binary trade update to the real Order Execution fanout event queue.

### Failing / blocked scenarios

#### Test-only Account authentication is rejected

- **Test/source:** `account migration and authenticated readiness`, `integration-tests/run_backend_integration.py`.
- **Services:** Account Service, PostgreSQL, local fake JWKS/JWT issuer.
- **Expected:** a local fake RS256 JWT, issued only by the test fake, authenticates `GET /api/v1/me` after successful migration.
- **Actual:** Account Service started and migrated, but returned HTTP 401 for the test token.
- **Classification:** **Test-environment defect / blocked Account API scenario.** This does not test or diagnose Keycloak, which remains explicitly out of scope.
- **Next direction:** capture Spring Security's JWT validation reason and align the fake issuer/JWK/token claims with the current resource-server configuration; retain the local-only signed-token approach.
- **Production fix applied:** none.

#### Duplicate approved order produces two broker submissions

- **Test/source:** `duplicate approved order is idempotent`, `integration-tests/run_backend_integration.py`.
- **Services:** RabbitMQ, Order Execution, fake Alpaca REST.
- **Scenario:** publish the same valid limit-order message twice to `orders.approved`.
- **Expected:** one broker submission or a durable duplicate decision.
- **Actual:** the fake broker recorded **two** `POST /v2/orders` requests; Order Execution logged two successful placements.
- **Classification:** **Critical production defect.**
- **Why it matters:** RabbitMQ redelivery or producer duplication can place duplicate orders.
- **Direction:** require an idempotency key/client-order ID, persist a submission ledger before broker dispatch, and use the same value as the broker client order ID.
- **Production fix applied:** none.

### Latest rerun result

| Passed | Failed / blocked | Conclusion |
|---:|---:|---|
| 8 | 2 | **NOT READY** — duplicate broker submission is a critical paper-trading correctness defect. |

## Account JWT diagnostic follow-up — 2026-08-19

The integration runner now starts Account Service with the test-only setting
`LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=TRACE`. This is intended to capture
the precise local JWT/JWKS validation failure without enabling production
security diagnostics or logging token material.

The diagnostic rerun was not executed because Docker was unavailable at the
local daemon socket before Compose could start PostgreSQL and RabbitMQ:

```text
Cannot connect to the Docker daemon at unix:///Users/luigi/.docker/run/docker.sock.
```

Classification: **Test-environment defect**. No fake-token change was made,
because there is not yet trace evidence identifying whether the mismatch is
the issuer, signature, key selection, timestamps, or another JWT validator.
Once Docker is available, rerun `python3 integration-tests/run_backend_integration.py`,
inspect the redacted Account Service trace, and make the smallest test-only
fake JWKS/JWT correction supported by that evidence.

## Account JWT diagnostic resolution — 2026-08-19

The Spring Security trace identified the exact rejection: `The iss claim is
not valid`. The fake JWT already used the intended local issuer
`http://127.0.0.1:18080/issuer`; its signature and JWK selection were not the
problem. The integration runner now sets the canonical, test-only Spring
property `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` directly to
that issuer, in addition to the local JWKS URL. This avoids ambiguity from
the application property's placeholder/inline-comment form without changing
production security configuration or making the fake token non-standard.

The rerun succeeded: Spring logged `Authenticated token`, the test principal
reached `GET /api/v1/me`, and Account Service provisioned/read the authenticated
user after Flyway migration.

Latest controlled result:

| Passed | Failed | Remaining conclusion |
|---:|---:|---|
| 9 | 1 | **NOT READY** — identical approved messages still produce two broker submissions. |

The only failing assertion is `duplicate approved order is idempotent`:
the fake broker recorded two submissions for the same approved order. This
remains a **Critical production defect**; no production idempotency fix was
applied.
