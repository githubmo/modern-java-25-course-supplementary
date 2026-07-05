# Hello-world skeletons

Two minimal, **compiling** Quarkus apps — a safe starting point for the build days so nobody
loses an afternoon to a broken `pom.xml`. Each one runs, serves a `/hello` endpoint, exposes
`/q/health`, and ships with one green test.

```
skeletons/
├─ kyc-service/          # Day 08 starting point — grows into the applicant-onboarding API
└─ screening-service/    # Day 10 starting point — grows into the Kafka consumer
```

These are **starting points, not solutions.** The finished services live in `../../apps`
(`kyc-service`, `screening-service`) and at the `checkpoint/day-NN` Git tags.

## Run one

```bash
cd kyc-service          # or: screening-service
./mvnw quarkus:dev      # live reload; Ctrl+C to stop
```

Then, in another terminal:

```bash
curl localhost:8080/hello          # -> "KYC Service is up — hello, world. ..."
curl localhost:8080/q/health       # -> {"status":"UP", ...}
```

Open <http://localhost:8080/q/dev/> for the Quarkus Dev UI.

### Running both at once

Both default to port **8080**. To run the screening skeleton alongside the KYC one, give it a
different port:

```bash
cd screening-service
./mvnw quarkus:dev -Dquarkus.http.port=8082
```

(The `apps/postman` collection's `screeningBaseUrl` defaults to `http://localhost:8082` to match.)

## Verify the test is green

```bash
./mvnw -B verify
```

A green build means the starting point is sound — from here, any red is something *you* changed.

## How you grow them

You don't copy the reference app — you **build up to it**. The skeleton ships the bare minimum;
add extensions as each brief tells you to, e.g.:

```bash
quarkus ext add hibernate-validator     # Day 08, Step 1
quarkus ext add jdbc-postgresql flyway hibernate-orm-panache   # Day 09
quarkus ext add messaging-kafka         # Day 09 / Day 10
```

> The REST extension is **Quarkus REST** (`quarkus-rest-jackson`) — already included. Ignore any
> older guide that says "RESTEasy Reactive"; that's the legacy name for the same thing.

Briefs: `../day-08-quarkus-fundamentals/README.md` · `../day-09-quarkus-persistence/README.md` ·
`../day-10-messaging-capstone/README.md`. Reference app: `../../apps`.
