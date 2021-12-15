

-- !Ups
CREATE TABLE fee_refunds_reports (
    payment_request TEXT NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    amount SATOSHIS_TYPE NOT NULL,
    refunded_on TIMESTAMPTZ NULL,
    CONSTRAINT fee_refund_reports_pk PRIMARY KEY (payment_request, currency)
);

-- !Downs
DROP TABLE fee_refunds_reports;