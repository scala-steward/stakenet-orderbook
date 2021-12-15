
-- !Ups
INSERT INTO clients(client_id) VALUES ('c04a34c8-24e5-4315-9442-2ae093e0a0aa');

INSERT INTO bot_maker_clients(
    name,
    client_id,
    secret,
    pays_fees
) VALUES (
    'ben',
    'c04a34c8-24e5-4315-9442-2ae093e0a0aa',
    'oIzjDduQW351Gfcmli42viQQFokdIDWLwQZqUEyf2a43u9QrGJeGu5ND1CwBlRIg',
    false
);

-- !Downs
DELETE FROM bot_maker_clients WHERE client_id = 'c04a34c8-24e5-4315-9442-2ae093e0a0aa';
DELETE FROM clients WHERE client_id = 'c04a34c8-24e5-4315-9442-2ae093e0a0aa';
