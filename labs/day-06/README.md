# Day 6 — Build the KYC database by hand, then make it reproducible

> Starting point: the running course stack. No `starter/` — you practise on the **real Postgres
> container** from Day 2, writing raw SQL. No Hibernate, Panache, or Quarkus today; Flyway appears
> only at the end, driven as a container, to turn your schema into a migration.

## Objective

Connect to PostgreSQL with `psql` from *inside* the Docker container, then design and build the KYC
database by hand: create the `applicants` and `screenings` tables with the right types and
constraints, relate them with a foreign key, load data, and answer real questions with `SELECT`,
`JOIN`, `GROUP BY`, and a window function. Watch constraints reject bad data, read an `EXPLAIN`, prove
a transaction `ROLLBACK`, query `jsonb`, take a backup, find and cancel a slow query, and finally turn
the schema into a Flyway migration and run the same thing from plain Java. By the end you can talk to a
database directly — and make that conversation repeatable.

## Concepts in play

- [Connecting with psql from inside the container](../../docs/content/day-06/index.md#connecting-with-psql-from-inside-the-container)
- [psql meta-commands](../../docs/content/day-06/index.md#psql-meta-commands)
- [Roles, users, and permissions](../../docs/content/day-06/index.md#roles-users-and-permissions)
- [Building the applicants table by hand](../../docs/content/day-06/index.md#building-the-applicants-table-by-hand)
- [Constraints: the database guards the vault](../../docs/content/day-06/index.md#constraints-the-database-guards-the-vault)
- [Relating tables: foreign keys, joins, and aggregates](../../docs/content/day-06/index.md#relating-tables-foreign-keys-joins-and-aggregates)
- [Indexes and EXPLAIN](../../docs/content/day-06/index.md#indexes-and-explain)
- [Transactions](../../docs/content/day-06/index.md#transactions)
- [Querying jsonb](../../docs/content/day-06/index.md#querying-jsonb)
- [Window functions and CTEs](../../docs/content/day-06/index.md#window-functions-and-ctes)
- [MVCC, dead tuples, and VACUUM](../../docs/content/day-06/index.md#mvcc-dead-tuples-and-vacuum)
- [Seeing and killing problem queries](../../docs/content/day-06/index.md#seeing-and-killing-problem-queries)
- [Migrations: the schema becomes code](../../docs/content/day-06/index.md#module-5-migrations-the-schema-becomes-code)

## Steps

1. **Bring up the stack and connect.** From the repo root, start the infrastructure and open a SQL
   shell *inside* the Postgres container:
   ```bash
   docker compose -f infra/compose.yaml up -d postgres kafka kafka-ui
   docker compose -f infra/compose.yaml exec postgres psql -U orders -d orders
   ```
   You should land at the `orders=#` prompt. Confirm where you are:
   ```sql
   SELECT version();
   SELECT current_user, current_database();
   ```
2. **Look around with meta-commands.** Tell SQL (`;`) from psql commands (`\`):
   ```text
   \l            -- databases
   \dn           -- schemas
   \dt           -- tables (likely "Did not find any relations.")
   \du           -- roles
   \timing on    -- report query durations from now on
   ```
   If you see leftover tables from a previous session,
   `DROP TABLE IF EXISTS screenings, applicants CASCADE;` to start clean.
3. **Create a read-only role.** Model permissions on a group, then confirm it exists with `\du`:
   ```sql
   CREATE ROLE kyc_readonly NOLOGIN;
   GRANT CONNECT ON DATABASE orders TO kyc_readonly;
   GRANT USAGE ON SCHEMA public TO kyc_readonly;
   ```
4. **Create the `applicants` table by hand.** This is the exact shape the app ships as `V1`. Type it,
   then read it back with `\d applicants`:
   ```sql
   CREATE TABLE applicants (
       id            bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
       full_name     varchar(128) NOT NULL,
       national_id   varchar(64)  NOT NULL,
       date_of_birth date         NOT NULL,
       country       varchar(64)  NOT NULL,
       email         varchar(256) NOT NULL,
       status        varchar(16)  NOT NULL DEFAULT 'PENDING',
       attributes    jsonb        NOT NULL DEFAULT '{}'::jsonb,
       created_at    timestamptz  NOT NULL DEFAULT now(),
       updated_at    timestamptz  NOT NULL DEFAULT now()
   );
   GRANT SELECT ON applicants TO kyc_readonly;   -- the group can now read it
   ```
5. **Add constraints, then break one on purpose.** A unique national id and a status check:
   ```sql
   ALTER TABLE applicants ADD CONSTRAINT applicants_national_id_key UNIQUE (national_id);
   ALTER TABLE applicants ADD CONSTRAINT applicants_status_chk
     CHECK (status IN ('PENDING','CLEARED','REVIEW','BLOCKED'));

   INSERT INTO applicants (full_name, national_id, date_of_birth, country, email) VALUES
     ('Alice Adams', 'NID-1001', '1990-04-12', 'GB', 'alice@example.com'),
     ('Bob Brown',   'NID-1002', '1985-11-30', 'IE', 'bob@example.com'),
     ('Chen Wei',    'NID-1003', '1996-02-19', 'SG', 'chen@example.com');

   INSERT INTO applicants (full_name, national_id, date_of_birth, country, email, status)
   VALUES ('Eve Bad', 'NID-1001', '1970-01-01', 'GB', 'eve@example.com', 'UNKNOWN');
   -- rejected twice over: duplicate national_id AND status not in the allowed set
   ```
6. **Use RETURNING and an UPSERT.** Get the generated id back, then make a re-insert idempotent:
   ```sql
   INSERT INTO applicants (full_name, national_id, date_of_birth, country, email)
   VALUES ('Dara O''Neill', 'NID-1004', '2000-07-07', 'IE', 'dara@example.com')
   RETURNING id, status, created_at;

   INSERT INTO applicants (full_name, national_id, date_of_birth, country, email)
   VALUES ('Alice Adams', 'NID-1001', '1990-04-12', 'GB', 'alice.new@example.com')
   ON CONFLICT (national_id) DO UPDATE SET email = EXCLUDED.email;   -- updates, does not duplicate
   ```
7. **Add a second table and a foreign key.** Create `screenings`, load it, and let the FK reject a bad
   row:
   ```sql
   CREATE TABLE screenings (
       id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
       applicant_id bigint      NOT NULL REFERENCES applicants (id),
       decision     varchar(16) NOT NULL CHECK (decision IN ('CLEARED','REVIEW','BLOCKED')),
       risk_score   integer     NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
       screened_at  timestamptz NOT NULL DEFAULT now()
   );
   INSERT INTO screenings (applicant_id, decision, risk_score) VALUES
     (1, 'CLEARED', 12), (2, 'REVIEW', 57), (3, 'BLOCKED', 91);

   INSERT INTO screenings (applicant_id, decision, risk_score)
   VALUES (999, 'CLEARED', 5);   -- FK rejects: applicant 999 does not exist
   ```
8. **Join, aggregate, and rank.** Show each screening with its applicant, count by country, then use a
   window function to rank risk within each country:
   ```sql
   SELECT a.full_name, s.decision, s.risk_score
   FROM screenings s JOIN applicants a ON a.id = s.applicant_id
   ORDER BY s.risk_score DESC;

   SELECT country, count(*) AS applicants
   FROM applicants GROUP BY country ORDER BY applicants DESC;

   SELECT a.country, a.full_name, s.risk_score,
          rank() OVER (PARTITION BY a.country ORDER BY s.risk_score DESC) AS risk_rank
   FROM applicants a JOIN screenings s ON s.applicant_id = a.id;
   ```
9. **Read a plan and add an index.** Use `EXPLAIN` before and after:
   ```sql
   EXPLAIN SELECT * FROM screenings WHERE applicant_id = 1;
   CREATE INDEX screenings_applicant_idx ON screenings (applicant_id);
   EXPLAIN SELECT * FROM screenings WHERE applicant_id = 1;
   ```
   On this tiny table the planner may keep using a sequential scan — that is correct. The skill is
   *reading the plan*.
10. **Prove a transaction is all-or-nothing.** Make a reckless change, then undo it:
    ```sql
    BEGIN;
    UPDATE applicants SET status = 'BLOCKED';   -- no WHERE: every row!
    SELECT id, status FROM applicants;          -- all BLOCKED, in this session only
    ROLLBACK;
    SELECT id, status FROM applicants;          -- back to how it was; nothing saved
    ```
11. **Store and query JSON.** Add attributes and query inside the `jsonb`:
    ```sql
    UPDATE applicants
    SET attributes = '{"channel":"web","risk_band":"low","pep":false}'::jsonb
    WHERE national_id = 'NID-1001';

    SELECT full_name, attributes ->> 'risk_band' AS risk_band
    FROM applicants WHERE attributes ->> 'channel' = 'web';

    SELECT full_name FROM applicants WHERE attributes @> '{"channel":"web"}';
    ```
12. **Troubleshoot like a DBA.** Reclaim dead tuples and inspect the live session list. In a *second*
    terminal, hold a lock, then find and cancel it from the first:
    ```sql
    -- terminal 1 (your psql): make some garbage, then vacuum it
    UPDATE applicants SET status = status;   -- rewrites every row (MVCC)
    VACUUM (VERBOSE, ANALYZE) applicants;
    SELECT relname, n_live_tup, n_dead_tup FROM pg_stat_user_tables WHERE relname = 'applicants';
    ```
    ```sql
    -- terminal 2: open a transaction and DON'T commit
    BEGIN; UPDATE applicants SET status = 'REVIEW' WHERE id = 1;   -- holds a row lock
    ```
    ```sql
    -- terminal 1: find the blocker, then cancel it
    SELECT pid, state, left(query, 40) FROM pg_stat_activity WHERE state <> 'idle';
    SELECT pg_cancel_backend(<pid-from-above>);
    ```
13. **Take a backup.** Leave psql (`\q`) and dump the database to a file:
    ```bash
    docker compose -f infra/compose.yaml exec -T postgres \
      pg_dump -U orders orders > kyc-backup.sql
    ```
    Open `kyc-backup.sql` — it is plain SQL: the `CREATE TABLE` and `INSERT` statements you typed.
14. **Make the schema reproducible with Flyway.** The `examples/postgres-flyway/` module already holds
    `V1__create_applicants.sql` (identical to what you built) and `V2__create_outbox.sql`. `V1`
    *creates* `applicants`, so first drop the tables you built by hand, then let Flyway recreate them:
    ```bash
    docker compose -f infra/compose.yaml exec postgres \
      psql -U orders -d orders -c 'DROP TABLE IF EXISTS screenings, applicants CASCADE;'
    docker run --rm \
      -v "$PWD/examples/postgres-flyway/db/migration:/flyway/sql" \
      -v "$PWD/examples/postgres-flyway/flyway.conf:/flyway/conf/flyway.conf" \
      flyway/flyway:10 migrate
    docker compose -f infra/compose.yaml exec postgres \
      psql -U orders -d orders -c 'SELECT version, description, success FROM flyway_schema_history;'
    ```
    These are the same files the `kyc-service` runs on Day 9 via `quarkus.flyway.migrate-at-start=true`.
    (Flyway reaches Postgres at `host.docker.internal:5432` — see `examples/postgres-flyway/flyway.conf`.)
15. **See it from Java.** Run the plain-JDBC example — the same connect / insert / query / commit /
    rollback you just did in `psql`, now in code:
    ```bash
    cd examples/postgres-jdbc && mvn -q compile exec:java
    ```

## Acceptance criteria

- [ ] You connected with `psql` *inside* the container and reached the `orders=#` prompt.
- [ ] `applicants` and `screenings` exist with their constraints (verify with `\d applicants`, `\d screenings`).
- [ ] You saw a `CHECK`, a `UNIQUE`, **and** a `FOREIGN KEY` reject bad data.
- [ ] A `JOIN` + `GROUP BY` and a **window function** answered real questions about applicants and risk.
- [ ] A transaction `ROLLBACK` undid a change that no `SELECT` afterwards could see.
- [ ] You found a session in `pg_stat_activity` and cancelled it with `pg_cancel_backend`.
- [ ] `kyc-backup.sql` exists and contains readable SQL.
- [ ] Flyway applied `V1`/`V2` and `flyway_schema_history` shows both as `success = t`.

## Stretch goals

- Add a **GIN index** for `jsonb` containment and re-`EXPLAIN` the `@>` query:
  `CREATE INDEX applicants_attrs_idx ON applicants USING gin (attributes);`
- Write the **`risk_band(score)` PL/pgSQL function** from the doc and use it in a join.
- Filter groups with **`HAVING`**: countries with more than one applicant.
- Set a **`statement_timeout`** and prove a `SELECT pg_sleep(5)` is cancelled.
- Check the **cache-hit ratio** with the monitoring query from the doc and reason about what a low
  ratio would mean.
- Restore `kyc-backup.sql` into a scratch database to prove it round-trips.
