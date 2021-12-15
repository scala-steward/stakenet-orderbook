
-- !Ups
INSERT INTO clients(client_id) VALUES ('382338c5-842e-4475-a753-018741781ccb');

INSERT INTO bot_maker_clients(
    name,
    client_id,
    secret,
    pays_fees
) VALUES (
    'lssd-1',
    '382338c5-842e-4475-a753-018741781ccb',
    '1ZRNdXufZ78ugAizkQACxSpxKHFjadpvse4wM9QJn2W5P4i6hmxqzjuxFXqQPNkx',
    false
);

INSERT INTO clients(client_id) VALUES ('47feccb5-d083-4aa3-9e62-ed1c445fee94');

INSERT INTO bot_maker_clients(
    name,
    client_id,
    secret,
    pays_fees
) VALUES (
    'lssd-2',
    '47feccb5-d083-4aa3-9e62-ed1c445fee94',
    'ZXV2TSvFFP6MzATCSKKXZol1blSXPtMCw90FfxfSrwRXho4YBLHB2UPqrhAeuqFF',
    false
);

-- !Downs
DELETE FROM bot_maker_clients WHERE client_id = '382338c5-842e-4475-a753-018741781ccb';
DELETE FROM clients WHERE client_id = '382338c5-842e-4475-a753-018741781ccb';

DELETE FROM bot_maker_clients WHERE client_id = '47feccb5-d083-4aa3-9e62-ed1c445fee94';
DELETE FROM clients WHERE client_id = '47feccb5-d083-4aa3-9e62-ed1c445fee94';
