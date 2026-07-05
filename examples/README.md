# examples/

Standalone, plain-Java (non-Quarkus) demos that bridge the hand-driven Day 6 (Postgres)
and Day 7 (Kafka) CLI work to real client code. They run against the shared infra in
`infra/compose.yaml` and are **not** part of the application build or the checkpoint
gate (`./mvnw verify`) — but they do compile and run.

**Requires JDK 25.**

## Bring up the infra

```bash
docker compose -f infra/compose.yaml up -d postgres kafka kafka-ui
```

Host ports: Postgres `localhost:5432` (db `orders`, user/pass `orders`), Kafka
`localhost:29092`, Kafka UI `http://localhost:8081`.

## Modules

| Module | Shows | Run |
| --- | --- | --- |
| [`postgres-jdbc/`](postgres-jdbc/) | Raw JDBC: DriverManager, PreparedStatement, ResultSet, transactions | `cd postgres-jdbc && mvn -q compile exec:java` |
| [`kafka-clients/`](kafka-clients/) | Raw Apache Kafka producer + consumer | `cd kafka-clients && mvn -q compile exec:java` (producer); `... -Dexec.mainClass=com.databytes.examples.kafka.ConsumerMain` (consumer) |
| [`postgres-flyway/`](postgres-flyway/) | Versioned migrations via the Flyway Docker image (same SQL the app runs) | `cd postgres-flyway && docker run --rm -v "$PWD/db/migration:/flyway/sql" -v "$PWD/flyway.conf:/flyway/conf/flyway.conf" flyway/flyway:10 migrate` |

Each module has its own README with prerequisites, run commands, and how to cross-check.
