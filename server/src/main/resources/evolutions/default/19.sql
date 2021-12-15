

-- !Ups
ALTER TABLE partial_orders ALTER COLUMN payment_hash DROP NOT NULL;
-- we have to remove the burned_fee_amount column because it has rounding problems
ALTER TABLE partial_orders DROP COLUMN burned_fee_amount;


-- !Downs
ALTER TABLE partial_orders ADD COLUMN burned_fee_amount SATOSHIS_TYPE NOT NULL;
ALTER TABLE partial_orders ALTER COLUMN payment_hash SET NOT NULL;

