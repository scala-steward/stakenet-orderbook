
-- !Ups
ALTER TABLE partial_orders ADD COLUMN client_id UUID NULL;
ALTER TABLE partial_orders ADD CONSTRAINT partial_orders_clients_fk FOREIGN KEY (client_id) REFERENCES clients(client_id);

-- !Downs
ALTER TABLE partial_orders DROP COLUMN client_id;
