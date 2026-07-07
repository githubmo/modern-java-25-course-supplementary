# Day 8 — Copy-along: the finished KYC Service

> For anyone who wants to **see how it all fits** without building it from scratch. You will copy the
> finished Day 8 code, run it, and read a short guided tour. No typing the app out.
>
> Prefer the challenge? Do [`README.md`](./README.md) instead — this is the shortcut, not the lesson.

## 1. Get the finished code

Every day's reference app is saved at a Git tag. Check out Day 8's:

```bash
git switch --detach checkpoint/day-08
cd apps/kyc-service
```

When you're done and want your own branch back: `git switch -`.

Want to keep `main` open in your editor at the same time? Use a worktree instead of switching:

```bash
git worktree add ../kyc-day-08 checkpoint/day-08   # a sibling folder at checkpoint/day-08
cd ../kyc-day-08/apps/kyc-service
# later: git worktree remove ../kyc-day-08
```

## 2. Run it

No database, no Docker today — it runs on its own:

```bash
./mvnw quarkus:dev          # or: quarkus dev
```

Then, in another terminal:

```bash
# register an applicant -> 201 with a Location header
curl -i -X POST localhost:8080/applicants \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Ada Lovelace","nationalId":"LY-1001","dateOfBirth":"1990-05-20","country":"GB","email":"ada@example.com"}'

# a bad one -> 400 listing the offending fields
curl -i -X POST localhost:8080/applicants -H 'Content-Type: application/json' -d '{}'

# an unknown id -> 404
curl -i localhost:8080/applicants/999

# health
curl localhost:8080/q/health
```

Open the Dev UI at <http://localhost:8080/q/dev/> to see your beans and config. Run the tests with
`./mvnw -B verify`.

## 3. The tour — how the pieces fit

The request flows **`ApplicantResource` → `ApplicantService` → in-memory `Map`**, with DTOs on the
edge and exception mappers turning failures into a uniform JSON body. Read the files in that order.

| File | Its job |
|---|---|
| `api/RegisterApplicantRequest.java` | Incoming DTO. Bean Validation (`@NotBlank`, `@Past`, `@Email`) lives here, so bad input is rejected before any logic runs. |
| `api/ApplicantResource.java` | The HTTP layer. `POST /applicants` takes a `@Valid` request, returns `201` + `Location`; `GET /applicants/{id}`. It depends on `ApplicantService`, **not** on a `Map`. |
| `application/ApplicantService.java` | The logic. Today an in-memory store — this is the piece Day 9 swaps for a database. |
| `api/ApplicantResponse.java` | Outgoing DTO, built via `from(...)`, so the domain object never leaks over HTTP. |
| `api/ValidationExceptionMapper.java` / `ApplicantNotFoundExceptionMapper.java` | Turn a validation failure / missing id into a uniform `ApiError` (`400` / `404`). |
| `domain/Applicant.java` | Plain object (no persistence annotations yet) with `isAdult()`. |

**The one seam that matters today** — the store is behind DI, not hard-wired:

```java
// application/ApplicantService.java
@ApplicationScoped
public class ApplicantService {
    private final Map<Long, Applicant> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public Applicant register(Applicant draft) {
        draft.id = sequence.incrementAndGet();
        draft.status = ApplicantStatus.PENDING;
        draft.createdAt = Instant.now();
        store.put(draft.id, draft);
        return draft;
    }
    // findById(id) throws ApplicantNotFoundException when absent; list(page, size) is newest-first
}
```

Because `ApplicantResource` injects this class rather than a `Map`, Day 9 replaces the body with a
real database and the REST layer above it **does not change a line**. That is the whole point of the
in-memory day.

**Config profiles** — one key, three values (`application.properties`): `kyc.onboarding.message` has a
default, a `%dev` override, and a `%prod` value; `%test.quarkus.http.test-port=0` picks a random test
port. Same key, different environment.

## 4. Then move on

Next day starts from exactly this state: `git switch --detach checkpoint/day-09` (or read that day's
`COPY-ALONG.md`). The database goes in **underneath** this service.
