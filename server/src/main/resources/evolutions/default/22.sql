
-- !Ups
ALTER TABLE channel_rental_fees ADD COLUMN life_time_seconds BIGINT NULL;

UPDATE channel_rental_fees AS crf
  SET  life_time_seconds = cfp.life_time_seconds
FROM channel_fee_payments AS cfp
WHERE  cfp.payment_hash = crf.payment_hash;


-- !Downs
ALTER TABLE channel_rental_fees DROP COLUMN life_time_seconds;
