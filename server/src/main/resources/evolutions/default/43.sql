-- !Ups
CREATE TYPE MAKER_PAYMENT_STATUS_TYPE AS ENUM (
    'PENDING',
    'SENT',
    'FAILED'
);

CREATE TABLE maker_payments(
    maker_payment_id UUID NOT NULL,
    trade_id UUID NOT NULL,
    client_id UUID NOT NULL,
    amount SATOSHIS_TYPE NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    status MAKER_PAYMENT_STATUS_TYPE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    CONSTRAINT maker_payment_pk PRIMARY KEY (maker_payment_id),
    CONSTRAINT maker_payment_client_fk FOREIGN KEY (client_id) REFERENCES clients(client_id),
    CONSTRAINT maker_payment_trade_fk FOREIGN KEY (trade_id) REFERENCES historic_trades(trade_id)
);

CREATE INDEX maker_payments_client_id_index ON maker_payments USING BTREE (client_id);
CREATE INDEX maker_payments_trade_id_index ON maker_payments USING BTREE (trade_id);
