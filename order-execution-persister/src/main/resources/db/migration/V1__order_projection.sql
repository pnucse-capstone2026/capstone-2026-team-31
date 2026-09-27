create table order_projection (
    order_id uuid primary key,
    alpaca_order_id uuid not null unique,
    client_order_id varchar(128),
    symbol varchar(16) not null,
    side varchar(8),
    quantity numeric(24, 8),
    filled_quantity numeric(24, 8),
    average_filled_price numeric(24, 8),
    order_type varchar(32),
    time_in_force varchar(16),
    status varchar(32),
    fees numeric(24, 8),
    created_at timestamp with time zone,
    updated_at timestamp with time zone not null,
    filled_at timestamp with time zone,
    cancelled_at timestamp with time zone
);
