
-- !Ups
CREATE TABLE client_public_keys(
    client_public_key_id UUID NOT NULL,
    public_key BYTEA NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    client_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    CONSTRAINT client_public_keys_pk PRIMARY KEY (client_public_key_id),
    CONSTRAINT client_public_keys_public_key_currency_unique UNIQUE (public_key, currency),
    CONSTRAINT client_public_keys_client_fk FOREIGN KEY (client_id) REFERENCES clients(client_id),
    CONSTRAINT clients_public_keys_client_id_currency_unique UNIQUE (client_id, currency)
);

CREATE INDEX clients_public_keys_client_id_index ON client_public_keys(client_id);

-- !Downs
DROP TABLE client_public_keys;
