
-- !Ups
INSERT INTO currency_prices(currency, btc_price, usd_price, created_at)
SELECT 'BTC', 1, 18533.5769900708, '2015-01-01';

INSERT INTO currency_prices(currency, btc_price, usd_price, created_at)
SELECT 'LTC', 0.004554965278102585, 84.42677997067456, '2015-01-01';

INSERT INTO currency_prices(currency, btc_price, usd_price, created_at)
SELECT 'XSN', 0.000007730009479562484, 0.14353608617219, '2015-01-01';

-- !Downs
DELETE FROM currency_prices WHERE created_at =  '2015-01-01';
