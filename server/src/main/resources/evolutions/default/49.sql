-- !Ups
UPDATE channel_extension_requests SET fee = (fee * 10000000000)::SATOSHIS_TYPE;

UPDATE channel_fee_payments SET capacity = (capacity * 10000000000)::SATOSHIS_TYPE;
UPDATE channel_fee_payments SET fee = (fee * 10000000000)::SATOSHIS_TYPE;

UPDATE channel_rental_extension_fees SET amount = (amount * 10000000000)::SATOSHIS_TYPE;

UPDATE channel_rental_fee_details SET renting_fee = (renting_fee * 10000000000)::SATOSHIS_TYPE;
UPDATE channel_rental_fee_details SET transaction_fee = (transaction_fee * 10000000000)::SATOSHIS_TYPE;
UPDATE channel_rental_fee_details SET force_closing_fee = (force_closing_fee * 10000000000)::SATOSHIS_TYPE;

UPDATE channel_rental_fees SET fee_amount = (fee_amount * 10000000000)::SATOSHIS_TYPE;
UPDATE channel_rental_fees SET capacity = (capacity * 10000000000)::SATOSHIS_TYPE;
UPDATE channel_rental_fees SET funding_transaction_fee = (funding_transaction_fee * 10000000000)::SATOSHIS_TYPE;
UPDATE channel_rental_fees SET closing_transaction_fee = (closing_transaction_fee * 10000000000)::SATOSHIS_TYPE;

UPDATE fee_refunds SET amount = (amount * 10000000000)::SATOSHIS_TYPE;

UPDATE fee_refunds_reports SET amount = (amount * 10000000000)::SATOSHIS_TYPE;

UPDATE fees SET amount = (amount * 10000000000)::SATOSHIS_TYPE;

UPDATE historic_trades SET price = (price * 10000000000)::SATOSHIS_TYPE;
UPDATE historic_trades SET size = (size * 10000000000)::SATOSHIS_TYPE;
UPDATE historic_trades SET existing_order_funds = (existing_order_funds * 10000000000)::SATOSHIS_TYPE;

UPDATE liquidity_provider_logs SET amount = (amount * 10000000000)::SATOSHIS_TYPE;

UPDATE maker_payments SET amount = (amount * 10000000000)::SATOSHIS_TYPE;

UPDATE order_fee_invoices SET amount = (amount * 10000000000)::SATOSHIS_TYPE;

UPDATE order_fee_payments SET funds_amount = (funds_amount * 10000000000)::SATOSHIS_TYPE;
UPDATE order_fee_payments SET fee_amount = (fee_amount * 10000000000)::SATOSHIS_TYPE;

UPDATE partial_orders SET traded_amount = (traded_amount * 10000000000)::SATOSHIS_TYPE;
