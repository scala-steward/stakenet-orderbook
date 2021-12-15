
-- !Ups
CREATE TABLE order_fee_payments (
    payment_hash BYTEA NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    funds_amount SATOSHIS_TYPE NOT NULL,
    fee_amount SATOSHIS_TYPE NOT NULL,
    fee_percent NUMERIC NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT order_fee_payments_pk PRIMARY KEY (payment_hash, currency)
);

 -- This table is useful to calculate the burned amount in the partial orders,
 -- The fee burned amount is the quantity which belongs to us and cannot be refunded
CREATE TABLE partial_orders (
    order_id UUID NOT NULL,
    payment_hash BYTEA NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    traded_amount SATOSHIS_TYPE NOT NULL,
    burned_fee_amount SATOSHIS_TYPE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT partial_orders_pk PRIMARY KEY (order_id),
    CONSTRAINT partial_orders_order_fee_payments_fk  FOREIGN KEY (payment_hash, currency) REFERENCES order_fee_payments (payment_hash, currency)
);

CREATE TABLE channel_rental_fees (
     payment_hash BYTEA NOT NULL UNIQUE,
     paying_currency CURRENCY_TYPE NOT NULL,
     rented_currency CURRENCY_TYPE NOT NULL,
     fee_amount SATOSHIS_TYPE NOT NULL,
     capacity SATOSHIS_TYPE NOT NULL,
     funding_transaction BYTEA NULL,
     funding_transaction_fee SATOSHIS_TYPE NULL,
     closing_transaction BYTEA NULL,
     closing_transaction_fee SATOSHIS_TYPE NULL,
     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
     CONSTRAINT channel_rental_fees_pk PRIMARY KEY (payment_hash, paying_currency)
);

-- !Downs
DROP TABLE partial_orders;
DROP TABLE order_fee_payments;
DROP TABLE channel_rental_fees;
