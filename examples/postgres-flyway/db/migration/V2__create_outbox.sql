-- Day 9: the transactional outbox.
--
-- Written in the same transaction as the applicant it describes, so an applicant and its
-- "applicant-registered" event are persisted atomically. A polling dispatcher relays
-- PENDING rows to Kafka and marks them PROCESSED. `trace_parent` carries the W3C
-- trace context so the asynchronous dispatch can continue the original trace.

create table outbox_event (
    id             uuid          primary key,
    aggregate_type varchar(64)   not null,
    aggregate_id   varchar(64)   not null,
    type           varchar(64)   not null,
    payload        jsonb         not null,
    trace_parent   varchar(128),
    status         varchar(16)   not null default 'PENDING',
    created_at     timestamptz   not null default now(),
    processed_at   timestamptz
);

-- The poller only ever scans PENDING rows; a partial index keeps that cheap.
create index idx_outbox_pending on outbox_event (created_at) where status = 'PENDING';
