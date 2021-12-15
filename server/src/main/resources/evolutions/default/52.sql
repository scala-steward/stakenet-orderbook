-- !Ups
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

ALTER TABLE fee_refunds ADD COLUMN fee_refund_id UUID NOT NULL DEFAULT uuid_generate_v4();
ALTER TABLE fee_refund_fees DROP CONSTRAINT fee_refund_fees_fee_refunds_fk;
ALTER TABLE fee_refunds DROP CONSTRAINT fee_refunds_pk;
ALTER TABLE fee_refunds ALTER COLUMN payment_request DROP NOT NULL;
ALTER TABLE fee_refunds ADD CONSTRAINT fee_refunds_pk PRIMARY KEY (fee_refund_id);

ALTER TABLE fee_refund_fees ADD COLUMN fee_refund_id UUID NULL;
UPDATE fee_refund_fees r SET fee_refund_id = (SELECT fee_refund_id FROM fee_refunds WHERE payment_request = r.payment_request AND currency = r.currency);
ALTER TABLE fee_refund_fees ALTER COLUMN fee_refund_id SET NOT NULL;

ALTER TABLE fee_refund_fees DROP CONSTRAINT fee_refund_fees_pk;
ALTER TABLE fee_refund_fees ALTER COLUMN payment_request DROP NOT NULL;
ALTER TABLE fee_refund_fees ADD CONSTRAINT fee_refund_fees_pk PRIMARY KEY (fee_refund_id, r_hash, currency);
ALTER TABLE fee_refund_fees ADD CONSTRAINT fee_refund_fees_fee_refunds_fk FOREIGN KEY (fee_refund_id) REFERENCES fee_refunds(fee_refund_id);

ALTER TABLE fee_refunds_reports ADD COLUMN fee_refund_id UUID NULL;
UPDATE fee_refunds_reports r SET fee_refund_id = (SELECT fee_refund_id FROM fee_refunds WHERE payment_request = r.payment_request AND currency = r.currency);
ALTER TABLE fee_refunds_reports ALTER COLUMN fee_refund_id SET NOT NULL;
ALTER TABLE fee_refunds_reports DROP CONSTRAINT fee_refund_reports_pk;
ALTER TABLE fee_refunds_reports ALTER COLUMN payment_request DROP NOT NULL;
ALTER TABLE fee_refunds_reports ADD CONSTRAINT fee_refund_reports_pk PRIMARY KEY (fee_refund_id);
