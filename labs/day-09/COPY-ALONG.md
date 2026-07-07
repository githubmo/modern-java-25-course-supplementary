# Day 9 — Copy-along: the KYC Service on PostgreSQL

> For anyone who wants to **see how persistence slots in** without wiring it by hand. Copy the finished
> Day 9 code, run it against a throwaway Postgres, and read the tour.
>
> Prefer the challenge? Do [`README.md`](./README.md) instead.

## 1. Get the finished code

```bash
git switch --detach checkpoint/day-09
cd apps/kyc-service
```

Back to your branch later: `git switch -`. (Or use a worktree — see Day 8's copy-along.)

## 2. Run it

**Docker must be running.** Quarkus Dev Services starts a throwaway Postgres for you — there is no
connection string to configure.

```bash
./mvnw quarkus:dev
```

Prove it actually persists (the reason the database exists):

```bash
# register an applicant, note the id in the Location header
curl -i -X POST localhost:8080/applicants \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Ada Lovelace","nationalId":"LY-1001","dateOfBirth":"1990-05-20","country":"GB","email":"ada@example.com"}'
```

Stop dev mode (Ctrl-C), start it again, and `GET /applicants/{that-id}` — the applicant is still there.
Tests: `./mvnw -B verify` (they get their own Dev Services Postgres automatically).

## 3. The tour — how persistence slots in

The REST layer from Day 8 is untouched. Everything new lives **below** `ApplicantService`. Read in this
order:

| File | Its job |
|---|---|
| `src/main/resources/application.properties` | Turns persistence on. Note there is **no JDBC URL** for dev/test — the blank URL is what triggers Dev Services. Flyway owns the schema; Hibernate only validates. |
| `db/migration/V1__create_applicants.sql` | The schema, as versioned SQL. The filename *is* the version. |
| `domain/Applicant.java` | Now a real `@Entity` — `@Id @GeneratedValue`, `@Enumerated(STRING)` status, `@JdbcTypeCode(SqlTypes.JSON)` for the `jsonb` column. |
| `domain/ApplicantRepository.java` | Panache repository. `persist`/`findById` are inherited; only `listNewest(page, size)` is written by hand. |
| `application/ApplicantService.java` | Same public methods as Day 8, but delegating to the repository, with `@Transactional` on the write paths. |

**The swap that mattered** — the in-memory `Map` becomes an injected repository, and the method
signatures above it stay identical:

```java
// application/ApplicantService.java  (Day 8 had a Map here)
private final ApplicantRepository applicants;
// ...
@Transactional
public Applicant register(Applicant draft) {
    draft.status = ApplicantStatus.PENDING;
    applicants.persist(draft);   // IDENTITY key: draft.id is set on insert
    // ... (see §"what else is in here" below)
    return draft;
}
```

```java
// domain/ApplicantRepository.java
@ApplicationScoped
public class ApplicantRepository implements PanacheRepository<Applicant> {
    public List<Applicant> listNewest(int page, int size) {
        return findAll(Sort.by("createdAt").descending()).page(Page.of(page, size)).list();
    }
}
```

`@Transactional` is what makes a `register` all-or-nothing: the insert either fully commits or fully
rolls back.

### What else is already in here (Day 10's groundwork)

This checkpoint also contains the **transactional outbox** (`outbox/`, a second `V2__create_outbox.sql`
migration, the Kafka config in `application.properties`) and the observability plumbing. They are wired
but dormant — there is no broker and no consumer yet. They come alive on **Day 10**, so skim them now if
curious, but the Day 9 story is just: *the applicant survives a restart.*

## 4. Then move on

Day 10 starts from here and adds the **second service** that reacts to these applicants:
`git switch --detach checkpoint/day-10`.
