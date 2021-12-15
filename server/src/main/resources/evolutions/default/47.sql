-- !Ups
CREATE TABLE liquidity_providers(
    liquidity_provider_id UUID NOT NULL,
    client_id UUID NOT NULL,
    trading_pair TRADING_PAIR NOT NULL,
    principal_channel_identifier TEXT NOT NULL,
    hub_principal_channel_identifier TEXT NOT NULL,
    secondary_channel_identifier TEXT NOT NULL,
    hub_secondary_channel_identifier TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT liquidity_providers_pk PRIMARY KEY (liquidity_provider_id),
    CONSTRAINT liquidity_providers_client_id_fk FOREIGN KEY (client_id) REFERENCES clients(client_id)
);

CREATE INDEX liquidity_provider_client_id_index ON liquidity_providers USING BTREE(client_id);
CREATE INDEX liquidity_provider_trading_pair_index ON liquidity_providers USING BTREE(trading_pair);

CREATE TYPE LIQUIDITY_POOL_LOG_TYPE AS ENUM(
    'JOINED',
    'LEFT',
    'BOUGHT',
    'SOLD',
    'FEE'
);

CREATE TABLE liquidity_provider_logs(
    liquidity_provider_log_id UUID NOT NULL,
    liquidity_provider_id UUID NOT NULL,
    amount SATOSHIS_TYPE NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    liquidity_provider_log_type LIQUIDITY_POOL_LOG_TYPE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT liquidity_provider_logs_pk PRIMARY KEY (liquidity_provider_log_id),
    CONSTRAINT liquidity_provider_logs_liquidity_provider_fk FOREIGN KEY (liquidity_provider_id) REFERENCES liquidity_providers(liquidity_provider_id)
);

CREATE INDEX liquidity_provider_log_liquidity_provider_id_index ON liquidity_provider_logs USING BTREE(liquidity_provider_id);
