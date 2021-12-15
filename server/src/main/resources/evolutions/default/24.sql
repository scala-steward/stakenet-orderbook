
-- !Ups
CREATE TABLE channel_rental_fee_details(
    payment_hash BYTEA NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    renting_fee SATOSHIS_TYPE NOT NULL,
    transaction_fee SATOSHIS_TYPE NOT NULL,
    force_closing_fee SATOSHIS_TYPE NOT NULL,
    CONSTRAINT channel_rental_fees_detail_pk PRIMARY KEY (payment_hash, currency)
);

-- !Downs
DROP TABLE channel_rental_fee_details;
