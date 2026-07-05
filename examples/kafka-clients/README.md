# kafka-clients

A standalone plain-Java (non-Quarkus) demo of the raw Apache Kafka client (producer +
consumer).

## What it shows

- A `KafkaProducer<String,String>` with `acks=all` and `enable.idempotence=true`,
  sending ~5 records to `applicant-registered`, keyed by `applicantId`, value a JSON
  string matching the `ApplicantRegisteredEvent` contract. It prints each record's
  assigned partition and offset from the returned `RecordMetadata`.
- A `KafkaConsumer<String,String>` in group `examples-screening` with
  `auto.offset.reset=earliest` and `enable.auto.commit=false`, polling and
  `commitSync()`-ing manually, printing partition/offset/key/value for each record.
  It exits on its own after ~10s with no new records.

Both use try-with-resources for the client.

## Host port — read this

The broker's HOST listener is published on **`localhost:29092`**. The in-container
listener (`kafka:9092` / `9092`) is **not** reachable from the laptop, so this Java code
connects to `localhost:29092`. Only tooling running *inside* the compose network (e.g.
Kafka UI, or `kafka-consumer-groups.sh` run via `docker compose exec`) uses `kafka:9092`.

## Prerequisite

```bash
docker compose -f infra/compose.yaml up -d kafka kafka-ui
```

## Run

Producer (default main class):

```bash
mvn -q compile exec:java
```

Consumer:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.databytes.examples.kafka.ConsumerMain
```

## Cross-check

- Kafka UI: <http://localhost:8081> — inspect the `applicant-registered` topic,
  its partitions, messages, and consumer groups.
- Consumer group lag / offsets (note: `kafka-consumer-groups.sh` runs *inside* the
  container, so it uses the in-container `localhost:9092`):

```bash
docker compose -f infra/compose.yaml exec kafka \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group examples-screening
```

---

This is the raw-client version of the Day 7 Kafka CLI work. Day 10 replaces it with
SmallRye Reactive Messaging (`@Incoming` / `@Outgoing` channels) in the Quarkus apps.
