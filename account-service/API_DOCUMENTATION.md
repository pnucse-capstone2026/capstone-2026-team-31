# Account Service API — single Alpaca account

`ALPACA_API_KEY` and `ALPACA_API_SECRET` configure one paper account for the
whole service. They are never returned by the API.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/account` | Account and positions projection |
| GET | `/api/v1/dashboard` | Dashboard projection |
| POST | `/api/v1/account/reconcile` | Refresh account and positions from Alpaca |
| GET | `/api/v1/orders` | Recent orders from Order Persister |
| GET/POST | `/api/v1/watchlists` | List/create shared watchlists |
| GET/PUT/DELETE | `/api/v1/watchlists/{id}` | Read/replace/delete a watchlist |
| POST | `/api/v1/watchlists/{id}/entries` | Add an entry |
| PATCH/DELETE | `/api/v1/watchlists/{id}/entries/{ticker}` | Activate/deactivate or remove an entry |
| GET | `/internal/v1/portfolio` | Strategy's single-account portfolio snapshot |

The service intentionally has no user, connection, Keycloak, or OAuth routes.
