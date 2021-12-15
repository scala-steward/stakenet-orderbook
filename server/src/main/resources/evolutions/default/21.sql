
-- !Ups
CREATE TABLE channel_rental_extension_fees (
    payment_hash BYTEA NOT NULL,
    paying_currency CURRENCY_TYPE NOT NULL,
    rented_currency CURRENCY_TYPE NOT NULL,
    amount SATOSHIS_TYPE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT channel_rental_extension_fees_pk PRIMARY KEY (payment_hash, paying_currency)
);

-- !Downs
DROP TABLE channel_rental_extension_fees;
