-- !Ups
INSERT INTO clients(client_id) VALUES ('be4d165f-1bd7-4ff5-89f5-140f304d2203');

INSERT INTO bot_maker_clients(
    name,
    client_id,
    secret,
    pays_fees
) VALUES (
    'rostyslav-staging-tests',
    'be4d165f-1bd7-4ff5-89f5-140f304d2203',
    'YX9nDE6pcdKVzgfhG5r2FA5ymeFrtrBWjzfQtMvN5DhYScE8Dyp2ccxmQM8SX5zz',
    false
);

-- !Downs
DELETE FROM bot_maker_clients WHERE client_id = 'be4d165f-1bd7-4ff5-89f5-140f304d2203';
DELETE FROM clients WHERE client_id = 'be4d165f-1bd7-4ff5-89f5-140f304d2203';