
-- !Ups
CREATE TABLE clients(
    client_id UUID NOT NULL,
    created_on TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    CONSTRAINT clients_pk PRIMARY KEY (client_id)
);

CREATE TABLE wallet_clients(
    wallet_id TEXT NOT NULL,
    client_id UUID NOT NULL,
    CONSTRAINT wallet_clients_pk PRIMARY KEY (client_id),
    CONSTRAINT wallet_clients_wallet_id_unique UNIQUE (wallet_id),
    CONSTRAINT wallet_client_client_fk FOREIGN KEY (client_id) REFERENCES clients(client_id)
);

CREATE TABLE bot_maker_clients(
    name TEXT NOT NULL,
    client_id UUID NOT NULL,
    secret TEXT NOT NULL,
    pays_fees BOOLEAN NOT NULL,
    CONSTRAINT bot_maker_clients_pk PRIMARY KEY (client_id),
    CONSTRAINT bot_maker_clients_name_unique UNIQUE(name),
    CONSTRAINT bot_maker_client_client_fk FOREIGN KEY (client_id) REFERENCES clients(client_id)
);

INSERT INTO clients(client_id) VALUES
    ('11b4e5a8-68a0-4bdf-b11e-e52d39804fbe'),
    ('e354b922-b537-432f-881c-10cb29e303df'),
    ('32f208d1-206c-4b75-8e34-a0f6fbd59db9'),
    ('d361540d-b5bf-4ac3-93e8-693d0f8fe6bd'),
    ('a1f4c407-836e-4b8d-8d1d-6668287c5d32'),
    ('ab5732dd-0d23-4571-ba20-5e5f5cc44b3a'),
    ('d6317814-6092-487f-b005-0917644499da'),
    ('c247face-9388-4871-a897-6187de9a850f'),
    ('260e3692-0e2d-48a5-aded-5c5184ab0864');

INSERT INTO bot_maker_clients(name, client_id, secret, pays_fees) VALUES
    ('bot1.xsnbot.com', '11b4e5a8-68a0-4bdf-b11e-e52d39804fbe', 'vf3UPr8yL7cyo4fMoFpyFbspo9kQNXkCatnLFU25ZVvrV25CepB4wN69YtuD', false),
    ('Winnie', 'e354b922-b537-432f-881c-10cb29e303df', 'TEHvg5YlumQgnLTIAcF5lhvG2KLT3SFF04u1ZsZ58M', false),
    ('JoLee', '32f208d1-206c-4b75-8e34-a0f6fbd59db9', 'C0Cs6ecf4uz2fg2bjH1tG5TdDtkRhye9nIxy41x9py', false),
    ('Hermes', 'd361540d-b5bf-4ac3-93e8-693d0f8fe6bd', 'OL94sTUG6e0kk4bu6BO1IP3OfhKeYrjllj5h6McI0W', false),
    ('Matvei', 'a1f4c407-836e-4b8d-8d1d-6668287c5d32', 'bKeiNgnCVLw3tdCHpytgCSUZQNq4v5dmka92snPENf', false),
    ('Azuki', 'ab5732dd-0d23-4571-ba20-5e5f5cc44b3a', 'AHesBFKCL1JEtKyu3lBIPRP12qbN4tuTzlJ0isZQnP', true),
    ('SuperEpicMan', 'd6317814-6092-487f-b005-0917644499da', 'i8PNmCyMpZYCE9dHwMjeUd8uTzWC5yU29EEkJDZGLTWyBc', true),
    ('lilithoxia', 'c247face-9388-4871-a897-6187de9a850f', 'HZvqiARshb9cnuhhUi2Hu3RsrvUJXyQhqfMdzpstnKQKBs6k7R', true),
    ('bryn', '260e3692-0e2d-48a5-aded-5c5184ab0864', 'XddkBcWZiNJXHcBajMo4KUZHJetAeeJrktRqVfDfaVxqeV7aHP', true);


-- !Downs
DROP TABLE wallet_clients;
DROP TABLE bot_maker_clients;
DROP TABLE clients;

