
-- !Ups
ALTER TABLE fees ADD COLUMN fee_percent NUMERIC NOT NULL DEFAULT 0.0025;
-- drop the default because we don't really want one
ALTER TABLE fees ALTER COLUMN fee_percent DROP DEFAULT;

-- !Downs
ALTER TABLE fees DROP COLUMN fee_percent;
