# Day 8 — Scaffold the KYC Service

> Starting point: `git checkout checkpoint/day-07` (a clean repo, no app yet) — or the provided
> `starter/`. You will generate the app from scratch with the `quarkus` CLI.
>
> Just want to see how it fits without the build? Copy the finished code: [`COPY-ALONG.md`](./COPY-ALONG.md).

## Objective

Stand up the KYC Service: a Quarkus web service that registers applicants for onboarding, with
validated REST endpoints over an in-memory store, clean error responses, configuration profiles,
and health checks — all running in dev mode. No database and no Kafka today; the focus is the
framework, dependency injection, and the HTTP layer.

## Concepts in play

- [What a framework does for you](../../docs/content/day-08/index.md#what-a-framework-does-for-you)
- [Dependency injection, in plain terms](../../docs/content/day-08/index.md#dependency-injection-in-plain-terms)
- [Why Quarkus: build-time work](../../docs/content/day-08/index.md#why-quarkus-build-time-work)
- [A validated endpoint, end to end](../../docs/content/day-08/index.md#a-validated-endpoint-end-to-end)
- [Configuration profiles](../../docs/content/day-08/index.md#configuration-profiles)
- [Health checks](../../docs/content/day-08/index.md#health-checks)

## Steps

1. **Scaffold** — never copy a `pom.xml`:
   ```bash
   quarkus create app com.databytes:kyc-service \
     --extension=rest-jackson,hibernate-validator,smallrye-health
   ```
2. **Run dev mode** with `quarkus dev` (or `./mvnw quarkus:dev`). Open `http://localhost:8080/q/dev/`
   and find your beans in the Dev UI. Leave it running — you will edit code and watch it reload.
3. **Add the DTO records** — `RegisterApplicantRequest` (a record with Bean Validation: `@NotBlank`
   on `fullName`/`nationalId`/`country`, `@NotNull @Past LocalDate dateOfBirth`, `@NotBlank @Email
   email`) and `ApplicantResponse` with a `from(...)` factory, so the domain object never leaks into
   the HTTP layer.
4. **Write `ApplicantResource`** with an `@Inject`-ed `ApplicantService`, a `POST /applicants` that
   takes a `@Valid` request and returns `201` with a `Location` header, plus `GET /applicants/{id}`.
   A new applicant starts `PENDING`. Delegate to an in-memory `ApplicantService` — DI means you swap
   it for a real store on Day 9 without touching this class.
5. **Map errors** — add `ValidationExceptionMapper` (`ConstraintViolationException` → `400` with
   per-field violations) and a 404 mapper for an unknown applicant, both returning a uniform `ApiError`.
6. **Exercise it** with `curl`: a good applicant (`201`), a bad one (`400` listing the fields), and a
   missing id (`404`). Edit the resource, re-request, and watch live reload — no restart.
7. **Confirm health** — `curl localhost:8080/q/health` reports `UP`.

## Acceptance criteria

- [ ] `./mvnw -B verify` is green.
- [ ] `POST /applicants` returns `201` with a `Location` header; invalid input returns `400` with
      per-field violations; an unknown id returns `404` — all with the uniform `ApiError` body.
- [ ] `/q/health` reports `UP` (and `/q/health/live` and `/q/health/ready` respond).
- [ ] Dev mode live reload works: an edit is visible on the next request with no restart.
- [ ] Clean Git history (rebased, meaningful messages).

## Stretch goals

- Add a paginated `GET /applicants` with `@QueryParam("page")`/`@QueryParam("size")` and `@DefaultValue`.
- Add a `@QueryParam` filter to the list endpoint (e.g. by `country`).
- Write a custom Bean Validation constraint (e.g. a national-id format check) and use it on
  `RegisterApplicantRequest`.
- Inspect the resolved configuration and registered endpoints in the Dev UI at `/q/dev/`.
