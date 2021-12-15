
-- !Ups

CREATE TYPE TRADING_PAIR AS ENUM (
  'XSN_BTC',
  'XSN_LTC'
);

CREATE TYPE ORDER_SIDE AS ENUM (
  'Buy',
  'Sell'
);

CREATE DOMAIN SATOSHIS_TYPE AS NUMERIC(40)
CHECK (
  VALUE >= 0
);

CREATE TABLE historic_trades(
  trade_id UUID NOT NULL,
  trading_pair TRADING_PAIR NOT NULL,
  price SATOSHIS_TYPE NOT NULL,
  size SATOSHIS_TYPE NOT NULL,
  existing_order_id UUID NOT NULL,
  executing_order_id UUID NOT NULL,
  executing_order_side ORDER_SIDE NOT NULL,
  executed_on TIMESTAMPTZ NOT NULL,
  CONSTRAINT historic_trades_id_pk PRIMARY KEY (trade_id)
);

CREATE INDEX historic_trades_executed_on_index ON historic_trades USING BTREE (executed_on);

-- !Downs

DROP INDEX historic_trades_executed_on_index;
DROP TABLE historic_trades;
DROP DOMAIN SATOSHIS_TYPE;
DROP TYPE ORDER_SIDE;
DROP TYPE TRADING_PAIR;
