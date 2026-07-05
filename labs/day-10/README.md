# Day 10 — Messaging & Capstone

> Starting point: `git checkout checkpoint/day-09`.

## Objective

Wire the KYC Service to Kafka via Quarkus messaging so it **produces** `applicant-registered`, and
have the Screening Service **consume** it and run a background check — the same topic you drove by raw
CLI on Day 7, now driven in code. Then run the whole platform and demonstrate one applicant travelling
REST → PostgreSQL → Kafka → background check, end to end.

## Concepts in play

- [From the raw CLI to a declarative channel](../../docs/content/day-10/index.md#from-the-raw-cli-to-a-declarative-channel)
- [The event is a record](../../docs/content/day-10/index.md#the-event-is-a-record)
- [Producing and consuming](../../docs/content/day-10/index.md#producing-and-consuming)
- [The save-then-publish seam](../../docs/content/day-10/index.md#a-real-world-note-the-save-then-publish-seam)
- [What you built, end to end](../../docs/content/day-10/index.md#what-you-built-end-to-end)
- [Native images](../../docs/content/day-10/index.md#native-images)

## Steps

1. **Producer config.** In the KYC Service `application.properties`, declare the outgoing channel:
   `mp.messaging.outgoing.applicant-registered` with the `smallrye-kafka` connector,
   `topic=applicant-registered`, and a `StringSerializer` for the value. Point `%test` at the
   `smallrye-in-memory` connector so tests need no broker.
2. **Produce.** Inject a `@Channel("applicant-registered") MutinyEmitter<String>`. In `register`,
   build an `ApplicantRegisteredEvent` from the saved applicant, serialise it to JSON, and send it
   **keyed by the applicant id** (`OutgoingKafkaRecordMetadata.builder().withKey(...)`). Keep the key
   as the applicant id so one applicant's events stay on one partition.
3. **Consumer config.** In the Screening Service, declare
   `mp.messaging.incoming.applicant-registered` with the `smallrye-kafka` connector,
   `topic=applicant-registered`, a `group.id`, `auto.offset.reset=earliest`, and a
   `StringDeserializer`. Swap to `smallrye-in-memory` under `%test`.
4. **Consume.** Add an `@Incoming("applicant-registered")` `@Blocking` method that deserialises the
   payload to the Screening Service's own copy of `ApplicantRegisteredEvent`, runs a `BackgroundCheck`
   (watchlist + risk score → `CLEARED`/`REVIEW`/`BLOCKED`), records a `Screening`, and `ack`s. Use
   `String.formatted(...)` for log messages — not String Templates (absent in JDK 25).
5. **Run it end to end.** Build and start everything with
   `docker compose -f infra/compose.yaml --profile apps --profile observability up -d --build`, then
   `POST /applicants` and confirm a `201`, the message in kafka-ui (http://localhost:8081, topic
   `applicant-registered`), and a result from `GET /screenings`.
6. **Pick one hardening task:** dedupe the consumer on an `event-id` header (idempotency), route
   failures to a dead-letter topic (`failure-strategy=dead-letter-queue`), or add a container
   `HEALTHCHECK` so the whole system runs cleanly from Compose.
7. **(Demo, optional)** Build a native image of the KYC Service (`./mvnw -B package -Dnative`) and
   compare start-up time and resident memory against `./mvnw quarkus:dev`.

## Acceptance criteria

- [ ] `./mvnw -B verify` is green for both services (in-memory connector, Postgres via Dev Services).
- [ ] `docker compose -f infra/compose.yaml --profile apps --profile observability up -d --build`
      brings up Postgres, Kafka, and both services.
- [ ] A `POST /applicants` returns `201`, and the message appears on the `applicant-registered` topic
      in kafka-ui.
- [ ] The Screening Service **consumes** the event — visible via `GET /screenings` or its log — with
      no direct call between the two services.
- [ ] The same correlation id can be followed across both services' logs for one applicant.

## Stretch goals

- Make the consumer idempotent: dedupe on the `event-id` header and prove a redelivered event is
  skipped once, processed once.
- Close the save-then-publish seam with the transactional outbox (write the event in the same
  transaction, relay with a scheduled poller) and note where at-least-once delivery shows up.
- Add `quarkus-oidc` and protect `POST /applicants` with `@RolesAllowed`.
- Run the KYC Service as a native image and document the start-up and memory trade-offs you observed.
