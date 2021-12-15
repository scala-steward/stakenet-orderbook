
-- !Ups
ALTER TABLE channels ALTER payment_hash SET DATA TYPE BYTEA USING payment_hash::BYTEA;
ALTER TABLE channel_fee_payments ALTER payment_hash SET DATA TYPE BYTEA USING payment_hash::BYTEA;

-- !Downs
ALTER TABLE channels ALTER payment_hash SET DATA TYPE TEXT USING payment_hash::TEXT;
ALTER TABLE channel_fee_payments ALTER payment_hash SET DATA TYPE TEXT USING payment_hash::TEXT;
