
-- !Ups

-- This column should be NOT NULL but it will be allowed NULL for the moment
-- because we need to populate this column for older rows when the wallet starts
-- sending us its public keys, and we have to decide what to do with rows for
-- which we cannot get this value.
-- When this column is made NOT NULL then we can delete the column public_key from
-- channels since we already now the public key from client_public_key_id
ALTER TABLE channels ADD COLUMN client_public_key_id UUID NULL;

ALTER TABLE channels ADD CONSTRAINT channels_client_public_keys_fk
 FOREIGN KEY (client_public_key_id) REFERENCES client_public_keys(client_public_key_id);

CREATE INDEX channels_client_public_key_id_index ON channels(client_public_key_id);

-- !Downs
ALTER TABLE channels DROP COLUMN client_public_key_id;