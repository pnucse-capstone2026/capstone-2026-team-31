# Order-event contract

HTTP acceptance and Alpaca `trade_updates` are published to
`order-events-fanout` and persisted as one account-wide order projection.
Events have no user or connection identity.
