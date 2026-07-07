# Day 10 — Copy-along: the whole platform, end to end

> For anyone who wants to **watch one applicant travel REST → PostgreSQL → Kafka → background check**
> without wiring two services by hand. Copy the finished code, bring the whole system up with Compose,
> and read the tour.
>
> Prefer the challenge? Do [`README.md`](./README.md) instead.

## 1. Get the finished code

```bash
git switch --detach checkpoint/day-10
```

This is the final state: **both** services exist now — `apps/kyc-service` (producer) and
`apps/screening-service` (consumer). Back to your branch later: `git switch -`.

## 2. Run it — everything at once

Bring up Postgres, Kafka, both services, and the observability stack:

```bash
docker compose -f infra/compose.yaml --profile apps --profile observability up -d --build
```

Then drive one applicant through the whole platform:

```bash
# 1) register -> 201 (goes into Postgres, and an event into the outbox)
curl -i -X POST localhost:8080/applicants \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Ada Lovelace","nationalId":"LY-1001","dateOfBirth":"1990-05-20","country":"GB","email":"ada@example.com"}'

# 2) the message on the topic — open kafka-ui (port 8081), topic `applicant-registered`
open http://localhost:8081

# 3) the screening result — the second service consumed it, with no direct call between them
curl localhost:8082/screenings     # screening-service is published on host port 8082
```

Tear down: `docker compose -f infra/compose.yaml --profile apps --profile observability down`.
Tests for both services (no broker needed — in-memory connector): `./mvnw -B verify` in each service dir.

## 3. The tour — how the two services connect

Nothing calls the screening service directly. The KYC service writes an event; Kafka carries it; the
screening service reacts. Follow the event through four files:

| File | Its job |
|---|---|
| `kyc-service/.../application/ApplicantService.java` | On `register`, writes the applicant **and** an outbox row in **one transaction**. Does not touch Kafka itself. |
| `kyc-service/.../outbox/OutboxDispatcher.java` | A scheduled poller that relays committed outbox rows to Kafka — the "save-then-publish" seam done safely. Sends **keyed by applicant id** with an `event-id` header. |
| `screening-service/.../ScreeningConsumer.java` | `@Incoming` consumer: parse → dedupe on `event-id` → `BackgroundCheck` → record → `ack` (or `nack` to the dead-letter topic). |
| `screening-service/.../BackgroundCheck.java` | The decision: watchlist + risk score → `CLEARED` / `REVIEW` / `BLOCKED`. |

**The producing seam** — the applicant and its event commit together; a separate poller sends it later:

```java
// kyc-service ApplicantService.register(...)  — one transaction
applicants.persist(draft);          // the applicant
outbox.persist(record);             // its event, same commit — both or neither
```

```java
// kyc-service OutboxDispatcher — relays committed rows, at-least-once
@Scheduled(every = "{outbox.poll-interval}", concurrentExecution = SKIP)
void dispatch() { for (OutboxEvent e : store.nextBatch(batchSize)) relay(e); }
// relay(...) sends Message.of(json) keyed by applicant id, with an event-id header, then marks processed
```

**The consuming seam** — plain blocking code, one virtual thread per message:

```java
// screening-service ScreeningConsumer
@Incoming("applicant-registered")
@Blocking
@RunOnVirtualThread                                   // each message on its own virtual thread
public CompletionStage<Void> consume(Message<String> message) {
    var event = mapper.readValue(message.getPayload(), ApplicantRegisteredEvent.class);
    if (eventId != null && !store.markProcessed(eventId)) return message.ack();  // dedupe: at-least-once
    store.record(backgroundCheck.run(event));
    return message.ack();                              // nack(e) routes poison messages to the DLQ
}
```

Three ideas do the heavy lifting, and each earns its keep:
- **Outbox** — the event can't be lost even if Kafka is down at commit time (it's a committed DB row).
- **`event-id` dedupe** — at-least-once delivery means a message can arrive twice; processing is
  idempotent so it counts once.
- **`@Blocking @RunOnVirtualThread`** — the consumer stays simple imperative code, but a slow blocking
  call parks only a virtual thread, never a platform thread. (Trade-off: messages are screened
  concurrently, so per-partition ordering is dropped — fine here, each applicant is independent.)

## 4. That's the capstone

You've now seen the whole reference platform run. To rebuild any piece yourself, each day's
[`README.md`](./README.md) walks it from its checkpoint.
