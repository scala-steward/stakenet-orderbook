
-- !Ups
CREATE TABLE client_info_logs(
    client_info_log_id UUID NOT NULL,
    client_id UUID NOT NULL,
    rented_capacity_usd NUMERIC NOT NULL,
    hub_local_balance_usd NUMERIC NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    CONSTRAINT client_info_logs_pk PRIMARY KEY (client_info_log_id),
    CONSTRAINT client_info_logs_client_fk FOREIGN KEY (client_id) REFERENCES clients(client_id)
);

CREATE INDEX client_info_logs_client_id_index ON client_info_logs(client_id);
CREATE INDEX client_info_logs_create_index_index ON client_info_logs(created_at);

-- !Downs
DROP TABLE client_info_logs;
