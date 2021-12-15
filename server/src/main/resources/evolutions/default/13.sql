
-- !Ups
CREATE TABLE channel_extension_requests (
    payment_hash BYTEA NOT NULL,
    paying_currency CURRENCY_TYPE NOT NULL,
    channel_id UUID NOT NULL,
    fee SATOSHIS_TYPE NOT NULL,
    seconds BIGINT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT channel_extension_requests_pk PRIMARY KEY (payment_hash, paying_currency),
    CONSTRAINT channel_extension_requests_channels_fk FOREIGN KEY (channel_id) REFERENCES channels(channel_id)
);

CREATE TABLE channel_extension_fee_payments (
    payment_hash BYTEA NOT NULL,
    paying_currency CURRENCY_TYPE NOT NULL,
    paid_at TIMESTAMPTZ NOT NUll,
    CONSTRAINT channel_extension_fee_payments_pk PRIMARY KEY (payment_hash, paying_currency),
    CONSTRAINT channel_extension_fee_payments_channel_extension_requests_fk FOREIGN KEY (payment_hash, paying_currency) REFERENCES channel_extension_requests(payment_hash, paying_currency)
);

-- !Downs
DROP TABLE channel_extension_fee_payments;
DROP TABLE channel_extension_requests;
