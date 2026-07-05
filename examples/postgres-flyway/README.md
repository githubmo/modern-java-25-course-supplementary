# postgres-flyway

Run the **same** Flyway migrations the Quarkus KYC service uses, but via the official
Flyway Docker image instead of at app startup. No Maven module here — the point is to see
versioned migrations applied to the shared Postgres exactly as a migration tool does it.

## Versioned migrations

Flyway applies SQL files in order by version. The filename convention is:

```
V<n>__<description>.sql
```

- `V` marks a *versioned* (run-once, in order) migration.
- `<n>` is the version (1, 2, ...). Higher versions run after lower ones.
- Two underscores `__` separate the version from the human-readable description.

Files here:

```
db/migration/V1__create_applicants.sql
db/migration/V2__create_outbox.sql
```

These are **byte-for-byte the same files** the Quarkus app runs on Day 9 via
`quarkus.flyway.migrate-at-start=true`.

## The flyway_schema_history ledger

Flyway records every applied migration in a `flyway_schema_history` table: version,
description, checksum, who applied it, when, and whether it succeeded. On the next run it
compares this ledger against the files on disk and only applies what is missing — that is
how migrations stay idempotent and ordered across environments.

## Prerequisite

Postgres from the shared infra (published on the host at `localhost:5432`):

```bash
docker compose -f infra/compose.yaml up -d postgres
```

## Run (macOS / host.docker.internal)

The Flyway container reaches the host's Postgres via `host.docker.internal` (see
`flyway.conf`). Run from this directory:

```bash
docker run --rm \
  -v "$PWD/db/migration:/flyway/sql" \
  -v "$PWD/flyway.conf:/flyway/conf/flyway.conf" \
  flyway/flyway:10 migrate
```

## Verify

Inspect the resulting table and the migration ledger:

```bash
docker compose -f infra/compose.yaml exec postgres \
  psql -U orders -d orders -c '\d applicants'

docker compose -f infra/compose.yaml exec postgres \
  psql -U orders -d orders -c 'select version, description, success from flyway_schema_history;'
```
