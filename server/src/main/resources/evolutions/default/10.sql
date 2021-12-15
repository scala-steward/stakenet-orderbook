
-- !Ups
CREATE TYPE REFUND_STATUS AS ENUM (
    'PROCESSING',
    'REFUNDED',
    'FAILED'
);

CREATE TABLE fee_refunds (
    payment_request TEXT NOT NULL,
    refunded_r_hash BYTEA NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    amount SATOSHIS_TYPE NOT NULL,
    status REFUND_STATUS NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    refunded_on TIMESTAMPTZ NULL,
    CONSTRAINT fee_refunds_pk PRIMARY KEY (payment_request, currency),
    CONSTRAINT fee_refunds_fees_fk FOREIGN KEY (refunded_r_hash, currency) REFERENCES fees(r_hash, currency),
    CONSTRAINT refunded_r_hash_unique UNIQUE(refunded_r_hash, currency)
);

-- !Downs
DROP TYPE REFUND_STATUS;
DROP TABLE fee_refunds;
