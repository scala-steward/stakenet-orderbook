
-- !Ups
CREATE TABLE ip_country_codes(
    ip INET NOT NULL,
    country_code TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ip_country_codes_pk PRIMARY KEY(ip)
);

-- !Downs
DROP TABLE ip_country_codes;
