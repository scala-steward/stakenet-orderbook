-- !Ups
INSERT INTO clients(client_id) VALUES ('07c1b66e-6e33-48e0-b44b-0a4ce0413014');

INSERT INTO bot_maker_clients(
    name,
    client_id,
    secret,
    pays_fees
) VALUES (
    'rostyslav-staging-tests-2',
    '07c1b66e-6e33-48e0-b44b-0a4ce0413014',
    'hCTDykuMRNuyMcuyhMDyaKuAPnZK7NHhxuzb92h8tQcgcznXx4',
    false
);

-- !Downs
DELETE FROM bot_maker_clients WHERE client_id = '07c1b66e-6e33-48e0-b44b-0a4ce0413014';
DELETE FROM clients WHERE client_id = '07c1b66e-6e33-48e0-b44b-0a4ce0413014';
