# Local backend integration suite

Run from the repository root:

```sh
python3 integration-tests/run_backend_integration.py
```

The runner starts only disposable RabbitMQ and PostgreSQL containers, starts a local Java fake Alpaca on loopback ports 18080--18083, packages the current Spring services (including Order Execution Persister), executes bounded end-to-end checks, stores redacted evidence in `integration-tests/artifacts/`, and always removes Compose containers and volumes. It deliberately uses no real Alpaca or public endpoint.

The PostgreSQL container initializes separate `accounts_it` and
`order_execution_it` databases. Before starting services, the runner executes
a one-shot Flyway container against Persister's own mounted migration files.
It then starts Order Execution Persister on the shared `integration` RabbitMQ
vhost, verifies its `order-events-persister` fanout binding, and supplies its
Account Service URL, fake `ORDER_EXECUTION_SERVICE` JWT, and fake broker base
URL.

The fake provider records paths, bodies, non-secret header names/values used by the services, and WebSocket subscription frames. It does not proxy traffic. The suite supplies one fake Alpaca API key/secret pair to all services and exercises no user, OAuth, or Keycloak flow.
