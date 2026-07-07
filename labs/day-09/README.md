# Day 9 — Give the KYC Service a database

> Starting point: `git checkout checkpoint/day-08` (the KYC Service skeleton with an in-memory
> store). Solution to check against: `git checkout checkpoint/day-09`.
>
> Just want to see how it fits without the build? Copy the finished code: [`COPY-ALONG.md`](./COPY-ALONG.md).

## Objective

Replace the in-memory `Map` in `ApplicantService` with real PostgreSQL persistence — an `applicants`
table reached through Java, the same way you wrote SQL by hand with `psql` on Day 6. By the end, a
registered applicant survives a restart, and the REST layer you built yesterday has not changed a line.

## Concepts in play

- [The object-relational gap](../../docs/content/day-09/index.md#the-object-relational-gap)
- [The datasource, and zero-config Dev Services](../../docs/content/day-09/index.md#the-datasource-and-zero-config-dev-services)
- [Flyway owns the schema; Hibernate validates](../../docs/content/day-09/index.md#flyway-owns-the-schema-hibernate-validates)
- [The entity, and the `jsonb` gotcha](../../docs/content/day-09/index.md#the-entity-and-the-jsonb-gotcha)
- [Panache: repository over active record](../../docs/content/day-09/index.md#panache-repository-over-active-record)
- [`@Transactional`: all-or-nothing, declared](../../docs/content/day-09/index.md#transactional-all-or-nothing-declared)
- [Light testing: prove it persists](../../docs/content/day-09/index.md#light-testing-prove-it-persists)

## Prerequisites

Docker must be running — Dev Services starts a throwaway Postgres for both dev mode and the tests.
"Cannot connect to Docker" almost always means Docker Desktop is off.

## Steps

1. **Add the persistence extensions** with the `quarkus` CLI (never hand-edit the `pom.xml`):
   ```bash
   quarkus extension add jdbc-postgresql hibernate-orm-panache flyway
   ```
2. **Configure the datasource** in `src/main/resources/application.properties` — four lines, and no
   URL for dev/test (the blank URL is what triggers Dev Services):
   ```properties
   quarkus.datasource.db-kind=postgresql
   quarkus.hibernate-orm.database.generation=none
   quarkus.flyway.migrate-at-start=true
   quarkus.hibernate-orm.mapping.format.global=ignore
   ```
3. **Write the V1 migration** at `src/main/resources/db/migration/V1__create_applicants.sql` — a
   `CREATE TABLE applicants` with typed columns (`full_name`, `national_id`, `date_of_birth`,
   `country`, `email`, `status`) and a `jsonb attributes` column. The file name *is* the version.
4. **Map the `Applicant` entity** — `@Entity`/`@Table(name = "applicants")`,
   `@Id`/`@GeneratedValue(IDENTITY)`, `@Enumerated(EnumType.STRING)` on `status`,
   `@JdbcTypeCode(SqlTypes.JSON)` on the `attributes` `Map`, and `@PrePersist`/`@PreUpdate` hooks for
   the timestamps.
5. **Add `ApplicantRepository`** — an `@ApplicationScoped` class implementing
   `PanacheRepository<Applicant>`, with one domain query, `listNewest(page, size)`. Everything else
   (`persist`, `findById`, …) is inherited.
6. **Wire it into `ApplicantService`** — inject the repository in the constructor, delete the `Map`
   and the manual id/timestamp/sort code, and make `register`, `findById`, and `list` delegate to the
   repository. Annotate the write paths with `@Transactional`. Do **not** touch `ApplicantResource` —
   the method signatures are unchanged on purpose.
7. **Prove persistence** in dev mode (`./mvnw quarkus:dev`): `POST /applicants`, note the id, stop dev
   mode (Ctrl-C), start it again, and `GET /applicants/{id}` — the applicant is still there.
8. **Add the light tests** — a unit test that hands `ApplicantService` a `mock(ApplicantRepository.class)`,
   and a `@QuarkusTest` + REST Assured `ApplicantResourceTest` that registers an applicant and asserts
   `201` with `status` `"PENDING"`. The test app gets its own Dev Services Postgres automatically.

## Acceptance criteria

- [ ] `./mvnw -B verify` is green (unit test + `@QuarkusTest` against Dev Services Postgres).
- [ ] `POST /applicants` **persists**: an applicant created in dev mode survives a restart and comes
      back on `GET /applicants/{id}`.
- [ ] Flyway applied `V1` at startup (visible in the boot log) and Hibernate validated the mapping —
      `quarkus.hibernate-orm.database.generation=none`.
- [ ] `ApplicantResource` is unchanged from `checkpoint/day-08` (the storage swap stayed below it).
- [ ] Clean Git history (rebased, meaningful messages).

## Stretch goals

- Add a `V2__…` migration (e.g. an index on `national_id`) to feel the "new file, never an edit"
  habit before it matters on Day 10's outbox table.
- Try the **active record** style on a throwaway branch (`Applicant extends PanacheEntity`,
  `applicant.persist()`) and notice why a `static` call is harder to unit-test than an injected repository.
- Watch the SQL Hibernate generates: set `quarkus.hibernate-orm.log.sql=true` and register an applicant.
- Add a `GET /applicants` list endpoint test that asserts newest-first ordering via `listNewest`.
