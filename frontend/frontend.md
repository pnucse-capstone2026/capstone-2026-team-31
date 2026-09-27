# Single-account web contract

The browser presents one paper-trading dashboard and watchlists. It calls:
`GET /api/v1/dashboard`, `GET /api/v1/account`, `POST /api/v1/account/reconcile`,
`GET /api/v1/orders`, and `/api/v1/watchlists`. No browser credential, user,
OAuth token, connection ID, or account-selection field exists.
