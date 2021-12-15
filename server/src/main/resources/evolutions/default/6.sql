
-- !Ups
ALTER TABLE fees ADD COLUMN paid_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_DATE - 1; -- set a default value for existing fees

-- drop the default because we don't really want one, we just need it for existing fees while adding the column
-- since we want it to be not null
ALTER TABLE fees ALTER COLUMN paid_at DROP DEFAULT;

-- !Downs
ALTER TABLE fees DROP COLUMN paid_at;
