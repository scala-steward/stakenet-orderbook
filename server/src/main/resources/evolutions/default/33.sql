
-- !Ups
INSERT INTO clients(client_id) VALUES ('52a27dcb-9417-4fbe-b564-50eaa8732280');

INSERT INTO bot_maker_clients(
    name,
    client_id,
    secret,
    pays_fees
) VALUES (
    'winnie-dex-addon',
    '52a27dcb-9417-4fbe-b564-50eaa8732280',
    'fQq3aqKVjKfpuMe5oCOqK46OWyAWYRBMAjf8ymaGjBB9G2g1tpK9rXpHkkyzQPL4',
    false
);

-- !Downs
DELETE FROM bot_maker_clients WHERE client_id = '52a27dcb-9417-4fbe-b564-50eaa8732280';
DELETE FROM clients WHERE client_id = '52a27dcb-9417-4fbe-b564-50eaa8732280';
