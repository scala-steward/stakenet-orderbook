-- !Ups
CREATE TABLE connext_preimages (
    preimage BYTEA NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT preimages_pk PRIMARY KEY (preimage, currency),
    CONSTRAINT preimages_hash_currency_unique UNIQUE (hash, currency)
);

CREATE INDEX client_public_identifiers_client_index ON client_public_identifiers(client_id);

ALTER TABLE fees DROP CONSTRAINT fee_order_fee_invoice_fk;
