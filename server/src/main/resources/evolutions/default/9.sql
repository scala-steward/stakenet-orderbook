
-- !Ups
ALTER TABLE channels ADD COLUMN funding_transaction_byte BYTEA;
UPDATE channels SET funding_transaction_byte = decode(funding_transaction, 'hex') ;
ALTER TABLE channels DROP COLUMN funding_transaction;
ALTER TABLE channels RENAME COLUMN funding_transaction_byte TO funding_transaction;


-- !Downs
ALTER TABLE channels ADD COLUMN funding_transaction_text TEXT;
UPDATE channels SET funding_transaction_text = encode(funding_transaction::BYTEA, 'hex') ;
ALTER TABLE channels DROP COLUMN funding_transaction;
ALTER TABLE channels RENAME COLUMN funding_transaction_text TO funding_transaction;


