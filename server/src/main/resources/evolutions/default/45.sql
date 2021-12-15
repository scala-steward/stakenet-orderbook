-- !Ups
CREATE TYPE CONNEXT_CHANNEL_STATUS AS ENUM (
    'ACTIVE',
    'CLOSED'
);

CREATE TABLE connext_channels (
    connext_channel_id UUID NOT NUll,
    client_public_identifier_id UUID NOT NULL,
    payment_hash BYTEA NOT NULL,
    paying_currency CURRENCY_TYPE NOT NULL,
    channel_address TEXT NOT NULL,
    status CONNEXT_CHANNEL_STATUS NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT connext_channels_pk PRIMARY KEY (connext_channel_id),
    CONSTRAINT connext_channels_fee_payment_unique UNIQUE (payment_hash, paying_currency),
    CONSTRAINT connext_channels_fee_payment_fk FOREIGN KEY (payment_hash, paying_currency) REFERENCES channel_fee_payments(payment_hash, paying_currency),
    CONSTRAINT connext_channels_client_public_identifier_fk FOREIGN KEY (client_public_identifier_id) REFERENCES client_public_identifiers(client_public_identifier_id)
);

CREATE INDEX connext_channels_fee_payment_index ON connext_channels USING BTREE (payment_hash, paying_currency);
CREATE INDEX connext_channels_client_public_identifier_index ON connext_channels USING BTREE (client_public_identifier_id);
