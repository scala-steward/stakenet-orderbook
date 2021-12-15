
-- !Ups
INSERT INTO clients(client_id) VALUES ('b4ee774a-a14d-4c5c-ba02-c6e23cf3f525');

INSERT INTO bot_maker_clients(
    name,
    client_id,
    secret,
    pays_fees
) VALUES (
    'winnie-bot-staging',
    'b4ee774a-a14d-4c5c-ba02-c6e23cf3f525',
    'nZtjNYwPKkfzrwyqe6v8XfFewC5Jh6xgNwCEn43npu9vbWCSmtDVNENukEJquE6t',
    false
);

-- !Downs
DELETE FROM bot_maker_clients WHERE client_id = 'b4ee774a-a14d-4c5c-ba02-c6e23cf3f525';
DELETE FROM clients WHERE client_id = 'b4ee774a-a14d-4c5c-ba02-c6e23cf3f525';

