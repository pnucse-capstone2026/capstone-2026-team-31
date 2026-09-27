-- Single-account reference schema. Credentials remain environment-managed.
create table trading_account (
    alpaca_account_id uuid primary key,
    is_paper boolean not null default true,
    cash_balance numeric(18,2) not null default 0,
    currency varchar(8) not null default 'USD',
    updated_at timestamptz not null default now()
);
create table watchlist (
    watchlist_id uuid primary key default gen_random_uuid(),
    watchlist_name varchar(64) not null unique,
    created_at timestamptz not null default now()
);
create table watchlist_entry (
    entry_id uuid primary key default gen_random_uuid(),
    watchlist_id uuid not null references watchlist(watchlist_id) on delete cascade,
    ticker varchar(16) not null,
    is_active boolean not null default true,
    unique (watchlist_id, ticker)
);
create table position_projection (
    ticker varchar(16) primary key,
    quantity numeric(18,6) not null,
    average_entry_price numeric(18,4),
    updated_at timestamptz not null default now()
);
create table order_projection (
    alpaca_order_id uuid primary key,
    ticker varchar(16) not null,
    side varchar(4) not null check (side in ('BUY','SELL','buy','sell')),
    quantity numeric(18,6),
    status varchar(32),
    updated_at timestamptz not null
);
