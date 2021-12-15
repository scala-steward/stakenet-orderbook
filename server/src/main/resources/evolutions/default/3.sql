
-- !Ups
ALTER TYPE TRADING_PAIR ADD VALUE 'LTC_BTC';

-- !Downs
DELETE FROM pg_enum
WHERE enumlabel = 'LTC_BTC'
AND enumtypid = (
  SELECT oid FROM pg_type WHERE typname = 'trading_pair'
);
