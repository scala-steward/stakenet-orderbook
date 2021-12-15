-- !Ups
INSERT INTO clients(client_id) VALUES ('a982737f-5140-40fb-aea7-1e0306f420ee');

INSERT INTO bot_maker_clients(
    name,
    client_id,
    secret,
    pays_fees
) VALUES (
    'Wintermute',
    'a982737f-5140-40fb-aea7-1e0306f420ee',
    '4G8GmdqJWwCGq5D26X4nKB96AMdTqyDke42V3t7hA4DTSZRken8mBe5tAEHTJ6Ta',
    false
);

-- !Downs
DELETE FROM bot_maker_clients WHERE client_id = 'a982737f-5140-40fb-aea7-1e0306f420ee';
DELETE FROM clients WHERE client_id = 'a982737f-5140-40fb-aea7-1e0306f420ee';