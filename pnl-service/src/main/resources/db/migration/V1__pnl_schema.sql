create table account_ledger (
    account_id varchar(128) primary key,
    cash_balance numeric(24, 8) not null,
    created_at timestamptz not null
);

create table processed_execution (
    execution_id varchar(255) primary key,
    broker_order_id varchar(255) not null,
    recorded_at timestamptz not null
);

create table processed_market_tick (
    event_id varchar(255) primary key,
    ticker varchar(32) not null,
    observed_at timestamptz not null
);

create table open_lot (
    id uuid primary key,
    ticker varchar(32) not null,
    entry_execution_id varchar(255) not null,
    opened_at timestamptz not null,
    unit_cost numeric(24, 8) not null,
    original_quantity numeric(24, 8) not null,
    remaining_quantity numeric(24, 8) not null
);
create index open_lot_ticker_opened_at_idx on open_lot(ticker, opened_at);

create table symbol_pnl (
    ticker varchar(32) primary key,
    latest_market_price numeric(24, 8),
    latest_price_at timestamptz,
    realized_pnl numeric(24, 8) not null
);

create table lot_match (
    id uuid primary key,
    closing_execution_id varchar(255) not null,
    opening_lot_id uuid not null,
    ticker varchar(32) not null,
    matched_quantity numeric(24, 8) not null,
    buy_price numeric(24, 8) not null,
    sell_price numeric(24, 8) not null,
    realized_pnl numeric(24, 8) not null
);

create table accounting_anomaly (
    id uuid primary key,
    execution_id varchar(255) not null unique,
    ticker varchar(32) not null,
    anomaly_type varchar(128) not null,
    details varchar(1000) not null,
    occurred_at timestamptz not null
);
