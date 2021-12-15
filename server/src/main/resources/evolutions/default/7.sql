
-- !Ups
ALTER TYPE CHANNEL_STATUS ADD VALUE 'CLOSING' BEFORE 'CLOSED';

-- !Downs
DELETE FROM pg_enum
WHERE enumlabel = 'CLOSING'
AND enumtypid = (
  SELECT oid FROM pg_type WHERE typname = 'channel_status'
);