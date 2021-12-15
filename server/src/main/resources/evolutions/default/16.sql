
-- !Ups
CREATE TABLE close_expired_channel_requests(
    channel_id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    requested_on TIMESTAMPTZ NOT NULL,
    CONSTRAINT close_expired_channel_request_pk PRIMARY KEY (channel_id),
    CONSTRAINT close_expired_channel_request_channel_fk FOREIGN KEY (channel_id) REFERENCES channels(channel_id)
);

-- !Downs
DROP TABLE close_expired_channel_requests;
