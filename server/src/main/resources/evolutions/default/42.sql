-- !Ups
CREATE TABLE client_public_identifiers (
    client_public_identifier_id UUID NOT NULL,
    public_identifier TEXT NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    client_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    CONSTRAINT client_public_identifiers_pk PRIMARY KEY (client_public_identifier_id),
    CONSTRAINT client_public_identifiers_public_identifier_currency_unique UNIQUE (public_identifier, currency),
    CONSTRAINT client_public_identifiers_client_fk FOREIGN KEY (client_id) REFERENCES clients(client_id),
    CONSTRAINT client_public_identifiers_client_id_currency_unique UNIQUE (client_id, currency)
)

-- !Downs
DROP TABLE client_public_identifiers;

