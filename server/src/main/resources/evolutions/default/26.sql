
-- !Ups

-- These functions allow to reverse bytea columns.
-- source: https://stackoverflow.com/a/25137344/3211175
CREATE OR REPLACE FUNCTION reverse_bytes_iter(bytes bytea, length int, midpoint int, index int)
RETURNS bytea AS
$$
  SELECT CASE WHEN index >= midpoint THEN bytes ELSE
    reverse_bytes_iter(
      set_byte(
        set_byte(bytes, index, get_byte(bytes, length-index)),
        length-index, get_byte(bytes, index)
      ),
      length, midpoint, index + 1
    )
  END;;
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION reverse_bytes(bytes bytea) RETURNS bytea AS
'SELECT reverse_bytes_iter(bytes, octet_length(bytes)-1, octet_length(bytes)/2, 0)'
LANGUAGE SQL IMMUTABLE;

-- reverse actual bytes
UPDATE channel_rental_fees
SET funding_transaction = reverse_bytes(COALESCE(funding_transaction, DECODE('', 'hex'))),
    closing_transaction = reverse_bytes(COALESCE(closing_transaction, DECODE('', 'hex')));

UPDATE channels
SET funding_transaction = reverse_bytes(COALESCE(funding_transaction, DECODE('', 'hex')));

-- !Downs

-- reverse the bytes to get the previous behavior
UPDATE channels
SET funding_transaction = reverse_bytes(COALESCE(funding_transaction, DECODE('', 'hex')));

UPDATE channel_rental_fees
SET funding_transaction = reverse_bytes(COALESCE(funding_transaction, DECODE('', 'hex'))),
    closing_transaction = reverse_bytes(COALESCE(closing_transaction, DECODE('', 'hex')));

-- drop the functions
DROP FUNCTION reverse_bytes;
DROP FUNCTION reverse_bytes_iter;