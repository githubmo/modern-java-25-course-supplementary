-- Day 9: the applicants table. Flyway owns the schema; Hibernate only validates
-- that the Applicant entity matches it. The `attributes` column is jsonb, so
-- schemaless per-applicant KYC data lives alongside the typed columns and stays queryable.

create table applicants (
    id            bigint generated always as identity primary key,
    full_name     varchar(128)  not null,
    national_id   varchar(64)   not null,
    date_of_birth date          not null,
    country       varchar(64)   not null,
    email         varchar(256)  not null,
    status        varchar(16)   not null,
    attributes    jsonb         not null default '{}'::jsonb,
    created_at    timestamptz   not null default now(),
    updated_at    timestamptz   not null default now()
);
