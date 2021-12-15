
-- !Ups
CREATE TABLE fee_refund_fees (
    payment_request TEXT NOT NULL,
    r_hash BYTEA NOT NULL,
    currency CURRENCY_TYPE NOT NULL,
    CONSTRAINT fee_refund_fees_pk PRIMARY KEY (payment_request, r_hash, currency),
    CONSTRAINT fee_refund_fees_fee_refunds_fk FOREIGN KEY(payment_request, currency) REFERENCES fee_refunds(payment_request, currency),
    CONSTRAINT fee_refund_fees_fees_fk FOREIGN KEY(r_hash, currency) REFERENCES fees(r_hash, currency),
    CONSTRAINT r_hash_currency_unique UNIQUE(r_hash, currency)
);

INSERT INTO fee_refund_fees(payment_request, r_hash, currency)
SELECT payment_request, refunded_r_hash, currency FROM fee_refunds;

ALTER TABLE fee_refunds DROP CONSTRAINT refunded_r_hash_unique;
ALTER TABLE fee_refunds DROP CONSTRAINT fee_refunds_fees_fk;
ALTER TABLE fee_refunds DROP COLUMN refunded_r_hash;

-- !Downs
ALTER TABLE fee_refunds ADD COLUMN refunded_r_hash BYTEA;
ALTER TABLE fee_refunds ADD CONSTRAINT fee_refunds_fees_fk FOREIGN KEY (refunded_r_hash, currency) REFERENCES fees(r_hash, currency);
ALTER TABLE fee_refunds ADD CONSTRAINT refunded_r_hash_unique UNIQUE(refunded_r_hash, currency);

UPDATE
  fee_refunds f
SET
  refunded_r_hash = (
    SELECT
      r_hash
    FROM fee_refund_fees
    WHERE payment_request = f.payment_request AND currency = f.currency
  );

ALTER TABLE fee_refunds ALTER COLUMN refunded_r_hash SET NOT NULL;

DROP TABLE fee_refund_fees;
