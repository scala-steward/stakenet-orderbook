-- !Ups
CREATE TABLE connext_channel_extension_fee_payments (
    payment_hash BYTEA NOT NULL,
    paying_currency CURRENCY_TYPE NOT NULL,
    paid_at TIMESTAMPTZ NOT NUll,
    CONSTRAINT connext_channel_extension_fee_payments_pk PRIMARY KEY (payment_hash, paying_currency),
    CONSTRAINT connext_channel_extension_fee_payments_requests_fk FOREIGN KEY (payment_hash, paying_currency) REFERENCES connext_channel_extension_requests(payment_hash, paying_currency)
);

CREATE INDEX connext_channel_extension_fee_payments_connext_requests_index ON connext_channel_extension_fee_payments USING BTREE (payment_hash, paying_currency);
