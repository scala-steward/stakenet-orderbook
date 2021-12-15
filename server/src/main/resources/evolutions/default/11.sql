
-- !Ups
ALTER TABLE historic_trades ADD COLUMN existing_order_funds SATOSHIS_TYPE NOT NULL DEFAULT 0;

ALTER TABLE historic_trades ALTER COLUMN existing_order_funds DROP DEFAULT;
-- !Downs
ALTER TABLE historic_trades DROP COLUMN existing_order_funds;
