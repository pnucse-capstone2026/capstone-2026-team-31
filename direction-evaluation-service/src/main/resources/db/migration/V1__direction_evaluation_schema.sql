create table prediction_input (
    prediction_id varchar(255) primary key,
    ticker varchar(32) not null,
    predicted_direction varchar(16) not null,
    probability_up numeric(12, 8) not null,
    confidence numeric(12, 8) not null,
    latest_price numeric(24, 8) not null,
    prediction_timestamp timestamptz not null,
    triggered_by varchar(128) not null,
    model_version varchar(255)
);

create table market_tick (
    event_id varchar(255) primary key,
    ticker varchar(32) not null,
    price numeric(24, 8) not null,
    observed_at timestamptz not null
);
create index market_tick_ticker_observed_at_idx on market_tick(ticker, observed_at);

create table direction_evaluation (
    prediction_id varchar(255) primary key,
    ticker varchar(32) not null,
    horizon_at timestamptz not null,
    status varchar(16) not null,
    confidence_band varchar(16) not null,
    evaluation_event_id varchar(255),
    evaluation_price numeric(24, 8),
    evaluation_timestamp timestamptz,
    actual_return numeric(24, 12),
    actual_direction varchar(16),
    correct boolean
);
create index direction_evaluation_pending_idx on direction_evaluation(status, horizon_at);
