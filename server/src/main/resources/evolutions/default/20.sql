
-- !Ups
ALTER TYPE TRADING_PAIR ADD VALUE 'XSN_WETH';
ALTER TYPE TRADING_PAIR ADD VALUE 'BTC_WETH';
ALTER TYPE CURRENCY_TYPE ADD VALUE 'WETH';

-- !Downs
ALTER TYPE TRADING_PAIR RENAME TO TRADING_PAIR_OLD;
CREATE TYPE TRADING_PAIR AS ENUM('XSN_BTC', 'XSN_LTC', 'LTC_BTC');
ALTER TABLE historic_trades ALTER COLUMN trading_pair TYPE TRADING_PAIR USING trading_pair::text::TRADING_PAIR;

DROP TYPE TRADING_PAIR_OLD;

ALTER TYPE CURRENCY_TYPE RENAME TO CURRENCY_TYPE_OLD;
CREATE TYPE CURRENCY_TYPE AS ENUM('XSN', 'LTC', 'BTC');

ALTER TABLE fees DROP CONSTRAINT fee_order_fee_invoice_fk;
ALTER TABLE fee_refund_fees DROP CONSTRAINT fee_refund_fees_fees_fk;
ALTER TABLE fee_refund_fees DROP CONSTRAINT fee_refund_fees_fee_refunds_fk;
ALTER TABLE order_fee_invoices ALTER COLUMN currency TYPE CURRENCY_TYPE USING currency::text::CURRENCY_TYPE;
ALTER TABLE fee_refund_fees ALTER COLUMN currency TYPE CURRENCY_TYPE USING currency::text::CURRENCY_TYPE;
ALTER TABLE fees ALTER COLUMN currency TYPE CURRENCY_TYPE USING currency::text::CURRENCY_TYPE;
ALTER TABLE fee_refunds ALTER COLUMN currency TYPE CURRENCY_TYPE USING currency::text::CURRENCY_TYPE;
ALTER TABLE fees ADD CONSTRAINT fee_order_fee_invoice_fk FOREIGN KEY(r_hash, currency) REFERENCES order_fee_invoices(payment_hash, currency);
ALTER TABLE fee_refund_fees ADD CONSTRAINT fee_refund_fees_fees_fk FOREIGN KEY(r_hash, currency) REFERENCES fees(r_hash, currency);
ALTER TABLE fee_refund_fees ADD CONSTRAINT fee_refund_fees_fee_refunds_fk FOREIGN KEY(payment_request, currency) REFERENCES fee_refunds(payment_request, currency);

ALTER TABLE channel_extension_fee_payments DROP CONSTRAINT channel_extension_fee_payments_channel_extension_requests_fk;
ALTER TABLE channel_extension_fee_payments ALTER COLUMN paying_currency TYPE CURRENCY_TYPE USING paying_currency::text::CURRENCY_TYPE;
ALTER TABLE channel_extension_requests ALTER COLUMN paying_currency TYPE CURRENCY_TYPE USING paying_currency::text::CURRENCY_TYPE;
ALTER TABLE channel_extension_fee_payments ADD CONSTRAINT channel_extension_fee_payments_channel_extension_requests_fk FOREIGN KEY(payment_hash, paying_currency) REFERENCES channel_extension_requests(payment_hash, paying_currency);

ALTER TABLE partial_orders DROP CONSTRAINT partial_orders_order_fee_payments_fk;
ALTER TABLE partial_orders ALTER COLUMN currency TYPE CURRENCY_TYPE USING currency::text::CURRENCY_TYPE;
ALTER TABLE order_fee_payments ALTER COLUMN currency TYPE CURRENCY_TYPE USING currency::text::CURRENCY_TYPE;
ALTER TABLE partial_orders ADD CONSTRAINT partial_orders_order_fee_payments_fk FOREIGN KEY(payment_hash, currency) REFERENCES order_fee_payments(payment_hash, currency);


ALTER TABLE channel_rental_fees ALTER COLUMN paying_currency TYPE CURRENCY_TYPE USING paying_currency::text::CURRENCY_TYPE;
ALTER TABLE channel_rental_fees ALTER COLUMN paying_currency TYPE CURRENCY_TYPE USING paying_currency::text::CURRENCY_TYPE;
ALTER TABLE channel_rental_fees ALTER COLUMN rented_currency TYPE CURRENCY_TYPE USING rented_currency::text::CURRENCY_TYPE;
ALTER TABLE fee_refunds_reports ALTER COLUMN currency TYPE CURRENCY_TYPE USING currency::text::CURRENCY_TYPE;
ALTER TABLE channel_fee_payments ALTER COLUMN currency TYPE CURRENCY_TYPE USING currency::text::CURRENCY_TYPE;
ALTER TABLE channel_fee_payments ALTER COLUMN paying_currency TYPE CURRENCY_TYPE USING paying_currency::text::CURRENCY_TYPE;

DROP TYPE CURRENCY_TYPE_OLD;
