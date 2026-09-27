# 24hours-trading-bot

An event-driven Alpaca paper-trading bot for one configured account. The whole
installation uses the same `ALPACA_API_KEY` and `ALPACA_API_SECRET`; it has no
user login, OAuth flow, account selector, or per-account routing.

Set the two credentials through your deployment secret manager for Account
Service, Order Execution Service, Order Execution Persister, Market Data, and
News Ingestion. Never commit them. The default broker URL is Alpaca paper
trading.

Core flow: market/news ingestion → predictions → strategy → approved order →
Order Execution → Alpaca → order projection. Account Service exposes the one
account dashboard, positions, recent orders, reconciliation, and watchlists.

Useful checks:

```sh
(cd account-service && ./mvnw test)
(cd order-execution-service && ./mvnw test)
(cd order-execution-persister && ./mvnw test)
(cd frontend/web && npm run build)
```
