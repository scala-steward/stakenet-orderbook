
-- !Ups
ALTER TABLE channels ADD COLUMN paying_currency CURRENCY_TYPE NULL;

UPDATE channels AS c
  SET  paying_currency = cfp.paying_currency
FROM channel_fee_payments AS cfp
WHERE cfp.payment_hash = c.payment_hash;

ALTER TABLE channels ALTER COLUMN paying_currency SET NOT NULL;

ALTER TABLE channels DROP CONSTRAINT channels_payment_hash_key;
ALTER TABLE channel_fee_payments DROP CONSTRAINT channel_fee_payments_pk;

ALTER TABLE channel_fee_payments ADD CONSTRAINT channel_fee_payments_pk PRIMARY KEY (payment_hash, paying_currency);
ALTER TABLE channels ADD CONSTRAINT channels_payment_hash_paying_currency_unique UNIQUE (payment_hash, paying_currency);
ALTER TABLE channels ADD CONSTRAINT channel_channel_fee_payment_fk FOREIGN KEY (payment_hash, paying_currency) REFERENCES channel_fee_payments(payment_hash, paying_currency);

-- !Downs
ALTER TABLE channels DROP COLUMN paying_currency;

ALTER TABLE channel_fee_payments DROP CONSTRAINT channel_fee_payments_pk;
ALTER TABLE channel_fee_payments ADD CONSTRAINT channel_fee_payments_pk PRIMARY KEY (payment_hash);
ALTER TABLE channels ADD CONSTRAINT channels_payment_hash_key UNIQUE(payment_hash);
