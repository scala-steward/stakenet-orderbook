
-- !Ups
CREATE TABLE currency_prices(
    currency CURRENCY_TYPE NOT NULL,
    btc_price DECIMAL NOT NULL,
    usd_price DECIMAL NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE INDEX currency_prices_currency_created_at_index ON currency_prices(currency, created_at);

-- !Downs
DROP TABLE currency_prices;
