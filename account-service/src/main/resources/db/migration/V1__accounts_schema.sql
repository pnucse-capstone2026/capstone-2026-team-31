create table trading_account_projection (
    projection_id uuid primary key,
    alpaca_account_id uuid not null, --Returned by Alpaca API as: 'id'
    account_number varchar(64),
    status varchar(32),
    currency varchar(8),
    cash numeric(24, 8),
    buying_power numeric(24, 8),
    non_marginable_buying_power numeric(24, 8),
    portfolio_value numeric(24, 8),
    equity numeric(24, 8),
    last_equity numeric(24, 8),
    long_market_value numeric(24, 8),
    account_blocked boolean not null default false,
    trading_blocked boolean not null default false,
    trade_suspended_by_user boolean not null default false,
    shorting_enabled boolean not null default false,
    last_synced_at timestamp with time zone --Managed locally
);

create table watchlist (
    watchlist_id uuid primary key,
    alpaca_watchlist_id uuid not null unique,
    name varchar(255) not null,
    sync_status varchar(32) not null,
    last_sync_error_code varchar(80),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (name)
);

create table watchlist_entry (
    entry_id uuid primary key,
    watchlist_id uuid not null references watchlist(watchlist_id),
    ticker varchar(16) not null,
    is_active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (watchlist_id, ticker)
);

create table position_projection (
    position_id uuid primary key,
    alpaca_asset_id uuid,
    symbol varchar(16) not null,
    quantity numeric(24, 8) not null,
    available_quantity numeric(24, 8),
    side varchar(8),
    average_entry_price numeric(24, 8),
    cost_basis numeric(24, 8),
    current_price numeric(24, 8),
    market_value numeric(24, 8),
    unrealized_pnl numeric(24, 8),
    updated_at timestamp with time zone not null,
    unique (symbol)
);
