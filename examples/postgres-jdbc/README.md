# postgres-jdbc

A standalone plain-Java (non-Quarkus) demo of raw JDBC against the course PostgreSQL.

## What it shows

- Connecting with `DriverManager` to `jdbc:postgresql://localhost:5432/orders`.
- `create table if not exists applicants (...)` using the canonical KYC schema.
- `PreparedStatement` inserts with `INSERT ... RETURNING id`, including a `jsonb`
  `attributes` value cast with `?::jsonb`, printing the generated ids.
- Iterating a `ResultSet` from a `SELECT`, reading the `jsonb` column as text.
- An explicit transaction: `setAutoCommit(false)`, an UPDATE, a `rollback()` that
  undoes it, then a committed UPDATE.

Everything uses try-with-resources for `Connection` / `Statement` / `ResultSet`.

## Prerequisite

Start Postgres from the shared infra (published on the host at `localhost:5432`):

```bash
docker compose -f infra/compose.yaml up -d postgres
```

## Run

```bash
mvn -q compile exec:java
```

## Confirm

```bash
docker compose -f infra/compose.yaml exec postgres psql -U orders -d orders -c '\dt'
```

---

This is the raw-JDBC version of the work Day 6 did by hand in `psql`. Day 9 replaces
it with Hibernate ORM with Panache and Flyway-managed migrations.
