
-- !Ups
CREATE TYPE CURRENCY_TYPE AS ENUM (
    'XSN',
    'LTC',
    'BTC'
);

CREATE TABLE fees (
    r_hash BYTEA NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    amount SATOSHIS_TYPE NOT NULL,
    locked_for_order_id UUID NULL, -- the order that can spend this fee, TODO: add foreign key, possibly unique constraint
    CONSTRAINT fees_pk PRIMARY KEY (r_hash, currency)
);

CREATE INDEX fees_locked_for_order_id_index ON fees USING BTREE (locked_for_order_id);


-- !Downs
DROP INDEX fees_locked_for_order_id_index;
DROP TABLE fees;
DROP TYPE CURRENCY_TYPE;
