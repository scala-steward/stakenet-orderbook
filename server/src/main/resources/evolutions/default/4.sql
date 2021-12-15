
-- !Ups

CREATE TYPE CHANNEL_STATUS AS ENUM (
    'OPENING',
    'ACTIVE',
    'CLOSED'
);

CREATE TABLE channels (
    channel_id UUID NOT NUll,
    payment_hash TEXT NOT NULL UNIQUE,
    public_key BYTEA NULL,
    funding_transaction TEXT NULL,
    output_index INT NULL,
    created_at TIMESTAMPTZ NULL,
    expires_at TIMESTAMPTZ NULL,
    channel_status CHANNEL_STATUS NOT NULL,
    CONSTRAINT channels_pk PRIMARY KEY (channel_id)
);

CREATE TABLE channel_fee_payments (
    currency CURRENCY_TYPE NOT NULL,
    paying_currency CURRENCY_TYPE NOT NULL,
    capacity SATOSHIS_TYPE NOT NULL,
    life_time_seconds BIGINT NOT NULL,
    fee SATOSHIS_TYPE NOT NULL,
    payment_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT channel_fee_payments_pk PRIMARY KEY (payment_hash)
);

-- !Downs
DROP TABLE channels;
DROP TABLE channel_fee_payments;
DROP TYPE CHANNEL_STATUS;