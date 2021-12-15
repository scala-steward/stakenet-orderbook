-- !Ups
CREATE TABLE connext_channel_contract_deployment_fees(
    transaction_hash TEXT NOT NULL,
    client_id UUID NOT NULL,
    amount SATOSHIS_TYPE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT connext_channel_contract_deployment_fees_pk PRIMARY KEY (transaction_hash),
    CONSTRAINT connext_channel_contract_deployment_fees_clients_fk FOREIGN KEY (client_id) REFERENCES clients(client_id),
    CONSTRAINT connext_channel_contract_deployment_fees_client_id_unique UNIQUE (client_id)
);
