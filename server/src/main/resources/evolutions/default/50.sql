-- !Ups
CREATE TABLE connext_channel_extension_requests (
    payment_hash BYTEA NOT NULL,
    paying_currency CURRENCY_TYPE NOT NULL,
    connext_channel_id UUID NOT NULL,
    fee SATOSHIS_TYPE NOT NULL,
    seconds BIGINT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT connext_channel_extension_requests_pk PRIMARY KEY (payment_hash, paying_currency),
    CONSTRAINT connext_channel_extension_requests_connext_channels_fk FOREIGN KEY (connext_channel_id) REFERENCES connext_channels(connext_channel_id)
);

CREATE INDEX connext_channel_extension_requests_connext_channel_id_index ON connext_channel_extension_requests USING BTREE (connext_channel_id);
