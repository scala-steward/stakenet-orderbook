
-- !Ups
ALTER TABLE channels ADD COLUMN closing_type TEXT NULL;
ALTER TABLE channels ADD COLUMN closed_by TEXT NULL;
ALTER TABLE channels ADD COLUMN closed_on TIMESTAMPTZ NULL;

-- !Downs
ALTER TABLE channels DROP COLUMN closing_type;
ALTER TABLE channels DROP COLUMN closed_by;
ALTER TABLE channels DROP COLUMN closed_on;
