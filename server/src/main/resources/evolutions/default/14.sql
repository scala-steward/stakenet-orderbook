
-- !Ups
CREATE TABLE order_fee_invoices (
    payment_hash BYTEA NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    amount SATOSHIS_TYPE NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT order_fee_invoices_pk PRIMARY KEY (payment_hash, currency)
);

-- We need to create the invoices for existing fees in order to add foreign key to fees,
-- amount and requested_at will have wrong values in most cases but at the moment this
-- evolution was made we only had test data so its not problem
INSERT INTO order_fee_invoices(payment_hash, currency, amount, requested_at)
SELECT
    r_hash, currency, amount, paid_at
FROM fees;

ALTER TABLE fees
ADD CONSTRAINT fee_order_fee_invoice_fk FOREIGN KEY (r_hash, currency) REFERENCES order_fee_invoices (payment_hash, currency);

-- !Downs
ALTER TABLE fees DROP CONSTRAINT fee_order_fee_invoice_fk;
DROP TABLE order_fee_invoices;
