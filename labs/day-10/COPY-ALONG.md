# Day 10 — Copy-along: the whole platform, end to end

> For anyone who wants to **watch one applicant travel REST → PostgreSQL → Kafka → background check**
> without wiring two services by hand. Day 10 is one new service — the whole thing is below.
>
> Prefer the challenge? Do [`README.md`](./README.md) instead.

## Get the code

**Fastest — check out the finished day:**

```bash
git switch --detach checkpoint/day-10
```

This is the final state: **both** services exist — `apps/kyc-service` (producer, unchanged since Day 9)
and `apps/screening-service` (consumer, entirely new). Back to your branch later: `git switch -`.

**Or — scaffold the new service** next to your Day 9 `kyc-service` and paste the files below:

```bash
quarkus create app com.databytes:screening-service \
  --extension=rest-jackson,smallrye-health,messaging-kafka,opentelemetry,micrometer-registry-prometheus,logging-json
cd screening-service
```

> **`kyc-service` does not change on Day 10.** Everything it needs — the outgoing Kafka channel, the
> outbox dispatcher — was already in the Day 9 checkpoint. Day 10 is purely the second service that
> consumes what the first one produces.

## Run it — everything at once

```bash
docker compose -f infra/compose.yaml --profile apps --profile observability up -d --build
```

Drive one applicant through the whole platform:

```bash
# 1) register -> 201 (into Postgres, and an event into the outbox)
curl -i -X POST localhost:8080/applicants -H 'Content-Type: application/json' \
  -d '{"fullName":"Ada Lovelace","nationalId":"LY-1001","dateOfBirth":"1990-05-20","country":"GB","email":"ada@example.com"}'

# 2) the message on the topic — open kafka-ui (port 8081), topic `applicant-registered`
open http://localhost:8081

# 3) the screening result — the second service consumed it, with no direct call between them
curl localhost:8082/screenings     # screening-service is published on host port 8082
```

Tear down: `docker compose -f infra/compose.yaml --profile apps --profile observability down`.
Tests (no broker — in-memory connector): `./mvnw -B verify` in each service directory.

## How the two services connect

Nothing calls the screening service directly. `kyc-service` writes an outbox row → `OutboxDispatcher`
relays it to Kafka (keyed by applicant id, with an `event-id` header) → `ScreeningConsumer` reacts.
Three ideas do the work: the **outbox** (event can't be lost), **`event-id` dedupe** (at-least-once
delivery counted once), and **`@Blocking @RunOnVirtualThread`** (simple blocking code, one virtual
thread per message).

---

## The files (all of `apps/screening-service`)

### `pom.xml`

REST + Health + Kafka messaging + observability. Test deps include the in-memory connector and
`junit-virtual-threads` (the pinning guard for the virtual-thread consumer).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.databytes</groupId>
    <artifactId>screening-service</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>quarkus</packaging>

    <properties>
        <compiler-plugin.version>3.15.0</compiler-plugin.version>
        <maven.compiler.release>25</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
        <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
        <quarkus.platform.version>3.33.2</quarkus.platform.version>
        <skipITs>true</skipITs>
        <surefire-plugin.version>3.5.4</surefire-plugin.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>${quarkus.platform.group-id}</groupId>
                <artifactId>${quarkus.platform.artifact-id}</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest-jackson</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-smallrye-health</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-messaging-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-opentelemetry</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-logging-json</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.27.7</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.smallrye.reactive</groupId>
            <artifactId>smallrye-reactive-messaging-in-memory</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.quarkus.junit</groupId>
            <artifactId>junit-virtual-threads</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>${quarkus.platform.group-id}</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.platform.version}</version>
                <extensions>true</extensions>
            </plugin>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>${compiler-plugin.version}</version>
                <configuration>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
            <plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${surefire-plugin.version}</version>
                <configuration>
                    <argLine>@{argLine}</argLine>
                    <systemPropertyVariables>
                        <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
                        <maven.home>${maven.home}</maven.home>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
            <plugin>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>${surefire-plugin.version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <argLine>@{argLine}</argLine>
                    <systemPropertyVariables>
                        <native.image.path>${project.build.directory}/${project.build.finalName}-runner</native.image.path>
                        <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
                        <maven.home>${maven.home}</maven.home>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>native</id>
            <activation>
                <property>
                    <name>native</name>
                </property>
            </activation>
            <properties>
                <quarkus.package.jar.enabled>false</quarkus.package.jar.enabled>
                <skipITs>false</skipITs>
                <quarkus.native.enabled>true</quarkus.native.enabled>
            </properties>
        </profile>
    </profiles>
</project>
```

### `src/main/resources/application.properties`

The incoming channel, plus a dead-letter topic for poison messages and the `%test` swap to the
in-memory connector.

```properties
## ---------------------------------------------------------------------------
## Screening Service configuration
##
## Consumes `applicant-registered` from Kafka and runs a background check. No
## database of its own. Dev uses Kafka Dev Services; prod reads the broker
## address from the environment.
## ---------------------------------------------------------------------------

quarkus.application.name=screening-service
quarkus.http.port=8080

## --- Messaging: incoming `applicant-registered` events from Kafka -----------
mp.messaging.incoming.applicant-registered.connector=smallrye-kafka
mp.messaging.incoming.applicant-registered.topic=applicant-registered
mp.messaging.incoming.applicant-registered.group.id=screening-service
mp.messaging.incoming.applicant-registered.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.applicant-registered.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.applicant-registered.auto.offset.reset=earliest
## Poison messages are routed to a dead-letter topic instead of blocking the partition.
mp.messaging.incoming.applicant-registered.failure-strategy=dead-letter-queue
mp.messaging.incoming.applicant-registered.dead-letter-queue.topic=applicant-registered-dlq

## --- Observability ---------------------------------------------------------
quarkus.micrometer.export.prometheus.enabled=true
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317

## --- Dev profile -----------------------------------------------------------
%dev.quarkus.log.console.json=false
%dev.quarkus.otel.sdk.disabled=true

## --- Test profile ----------------------------------------------------------
%test.quarkus.log.console.json=false
%test.quarkus.otel.sdk.disabled=true
%test.quarkus.http.test-port=0
## Swap Kafka for the in-memory connector so the consumer is tested without a broker.
%test.mp.messaging.incoming.applicant-registered.connector=smallrye-in-memory
%test.quarkus.kafka.devservices.enabled=false

## --- Prod profile ----------------------------------------------------------
%prod.quarkus.log.console.json=true
%prod.kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
%prod.quarkus.otel.sdk.disabled=false
%prod.quarkus.otel.exporter.otlp.endpoint=${QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT:http://otel-collector:4317}
```

### `events/ApplicantRegisteredEvent.java`

Its **own** copy of the contract — the topic is the contract, not a shared class.

```java
package com.databytes.screening.events;

import java.time.Instant;

/**
 * The Screening Service's own view of the {@code applicant-registered} contract.
 * It deliberately does not share a class with the KYC Service — the Kafka topic
 * is the contract, and each service evolves its copy independently.
 */
public record ApplicantRegisteredEvent(
        Long applicantId,
        String fullName,
        String nationalId,
        String country,
        String email,
        String status,
        Instant occurredAt,
        String correlationId) {
}
```

### `ScreeningConsumer.java` — the seam

`@Incoming` + `@Blocking @RunOnVirtualThread`: each message runs on its own virtual thread. Parse →
dedupe on `event-id` → run the check → record → `ack` (or `nack` to the dead-letter topic).

```java
package com.databytes.screening;

import com.databytes.screening.events.ApplicantRegisteredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

/**
 * Consumes {@code applicant-registered} events and runs a background check on each
 * applicant. The trace and correlation id from the KYC Service flow through
 * automatically (OpenTelemetry) and via the event body, so a single onboarding can
 * be followed across the Kafka boundary.
 *
 * <p>Delivery is at-least-once, so the consumer dedupes on the {@code event-id}
 * header. A failure nacks the message, which the channel's dead-letter strategy
 * routes to {@code applicant-registered-dlq} rather than blocking the partition.
 *
 * <p>{@code @Blocking @RunOnVirtualThread} runs each message on its own virtual
 * thread, so this stays plain blocking imperative code (parse, screen, record) yet a
 * slow blocking call parks only a virtual thread, never a carrier — the Day 4–5
 * virtual-thread model applied at the messaging layer. Note {@code @RunOnVirtualThread}
 * drops per-partition ordering (messages are processed concurrently); that is fine
 * here because each event is screened independently.
 */
@ApplicationScoped
public class ScreeningConsumer {

    private static final Logger LOG = Logger.getLogger(ScreeningConsumer.class);

    private final ScreeningStore store;
    private final BackgroundCheck backgroundCheck;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    @Inject
    public ScreeningConsumer(ScreeningStore store, BackgroundCheck backgroundCheck,
                             ObjectMapper mapper, MeterRegistry meters) {
        this.store = store;
        this.backgroundCheck = backgroundCheck;
        this.mapper = mapper;
        this.meters = meters;
    }

    @Incoming("applicant-registered")
    @Blocking
    @RunOnVirtualThread
    public CompletionStage<Void> consume(Message<String> message) {
        try {
            ApplicantRegisteredEvent event =
                    mapper.readValue(message.getPayload(), ApplicantRegisteredEvent.class);
            String eventId = eventId(message);
            MDC.put("correlationId", event.correlationId() != null ? event.correlationId() : "-");
            try {
                if (eventId != null && !store.markProcessed(eventId)) {
                    meters.counter("screenings.duplicate").increment();
                    LOG.infof("Duplicate event %s ignored", eventId);
                } else {
                    Screening screening = backgroundCheck.run(event);
                    store.record(screening);
                    meters.counter("screenings.processed").increment();
                    meters.counter("screenings.decision", "decision", screening.decision().name()).increment();
                    LOG.infof("Screened applicant %d (%s) -> %s (risk %d)",
                            event.applicantId(), event.fullName(),
                            screening.decision(), screening.riskScore());
                }
                return message.ack();
            } finally {
                MDC.remove("correlationId");
            }
        } catch (Exception e) {
            LOG.error("Failed to process applicant-registered event; routing to dead-letter topic", e);
            return message.nack(e);
        }
    }

    private static String eventId(Message<String> message) {
        return message.getMetadata(IncomingKafkaRecordMetadata.class)
                .map(meta -> meta.getHeaders().lastHeader("event-id"))
                .map(ScreeningConsumer::headerValue)
                .orElse(null);
    }

    private static String headerValue(Header header) {
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
```

### `BackgroundCheck.java`

The (simulated) decision: watchlist → `BLOCKED`, high-risk country → `REVIEW`, else `CLEARED` with a
deterministic risk score.

```java
package com.databytes.screening;

import com.databytes.screening.events.ApplicantRegisteredEvent;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * The (simulated) background check run when an applicant registers.
 *
 * <p><strong>Illustrative only.</strong> A real bank would call sanctions / PEP /
 * adverse-media and credit-reference providers. Here the rules are deterministic so
 * the course can reason about them: a watchlist match is {@code BLOCKED}, a high-risk
 * jurisdiction needs manual {@code REVIEW}, everyone else is {@code CLEARED} with a
 * low risk score derived from the national id.
 */
@ApplicationScoped
public class BackgroundCheck {

    // Obviously fake — placeholders so the demo has a deterministic "hit".
    private static final Set<String> WATCHLIST = Set.of("john doe", "jane roe", "ivan petrov");
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of("KP", "IR", "SY");

    public Screening run(ApplicantRegisteredEvent applicant) {
        String name = normalise(applicant.fullName());
        if (WATCHLIST.contains(name)) {
            return result(applicant, ScreeningDecision.BLOCKED, 100,
                    "Name matches the sanctions/PEP watchlist");
        }

        int base = baseRisk(applicant.nationalId());
        String country = normalise(applicant.country()).toUpperCase(Locale.ROOT);
        if (HIGH_RISK_COUNTRIES.contains(country)) {
            return result(applicant, ScreeningDecision.REVIEW, Math.max(base, 75),
                    "High-risk jurisdiction (" + country + ") — manual review required");
        }

        return result(applicant, ScreeningDecision.CLEARED, base,
                "Risk score " + base + " — cleared");
    }

    /** Deterministic 0..49 risk derived from the national id (String.hashCode is specified). */
    private static int baseRisk(String nationalId) {
        return nationalId == null ? 0 : Math.floorMod(nationalId.hashCode(), 50);
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Screening result(ApplicantRegisteredEvent a, ScreeningDecision decision,
                                    int risk, String reason) {
        return new Screening(a.applicantId(), a.fullName(), a.nationalId(),
                decision, risk, reason, Instant.now());
    }
}
```

### `Screening.java`

```java
package com.databytes.screening;

import java.time.Instant;

/** The result the service produced from a background check on a registered applicant. */
public record Screening(
        Long applicantId,
        String fullName,
        String nationalId,
        ScreeningDecision decision,
        int riskScore,
        String reason,
        Instant screenedAt) {
}
```

### `ScreeningDecision.java`

```java
package com.databytes.screening;

/** The outcome of a background check. */
public enum ScreeningDecision {
    /** Passed — the applicant can be onboarded. */
    CLEARED,
    /** Needs a human: a compliance officer must review before onboarding. */
    REVIEW,
    /** Failed — e.g. a watchlist match; onboarding is refused. */
    BLOCKED
}
```

### `ScreeningStore.java`

In-memory record of screenings, plus the `processedEventIds` set that gives dedupe its idempotency.

```java
package com.databytes.screening;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory record of completed screenings. The {@code processedEventIds} set
 * gives at-least-once delivery its idempotency: a redelivered event is
 * recognised and skipped. It is intentionally simple — a production service
 * would dedupe against a durable store (or rely on Kafka exactly-once) so the
 * guarantee survives a restart.
 */
@ApplicationScoped
public class ScreeningStore {

    private static final int MAX_RECENT = 100;

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
    private final Deque<Screening> recent = new ConcurrentLinkedDeque<>();

    /** @return {@code true} if this id is new, {@code false} if already processed. */
    public boolean markProcessed(String eventId) {
        return processedEventIds.add(eventId);
    }

    public void record(Screening screening) {
        recent.addFirst(screening);
        while (recent.size() > MAX_RECENT) {
            recent.removeLast();
        }
    }

    public List<Screening> recent() {
        return List.copyOf(recent);
    }

    public long processedCount() {
        return processedEventIds.size();
    }
}
```

### `ScreeningResource.java`

```java
package com.databytes.screening;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** Read-only view of recent screening results — handy for demos and the end-to-end test. */
@Path("/screenings")
@Produces(MediaType.APPLICATION_JSON)
public class ScreeningResource {

    @Inject
    ScreeningStore store;

    @GET
    public List<Screening> recent() {
        return store.recent();
    }
}
```

### `ScreeningLivenessCheck.java`

```java
package com.databytes.screening;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Liveness check that also surfaces how many applicants have been screened.
 * Complements the Kafka connector readiness check the messaging extension adds.
 */
@Liveness
@ApplicationScoped
public class ScreeningLivenessCheck implements HealthCheck {

    @Inject
    ScreeningStore store;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("screening-processing")
                .up()
                .withData("processed", store.processedCount())
                .build();
    }
}
```

---

## Tests

### `src/test/java/.../BackgroundCheckTest.java`

```java
package com.databytes.screening;

import static org.assertj.core.api.Assertions.assertThat;

import com.databytes.screening.events.ApplicantRegisteredEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BackgroundCheckTest {

    private final BackgroundCheck check = new BackgroundCheck();

    private static ApplicantRegisteredEvent applicant(String name, String nationalId, String country) {
        return new ApplicantRegisteredEvent(1L, name, nationalId, country, "x@example.com",
                "PENDING", Instant.now(), "corr-1");
    }

    @Test
    void watchlistMatchIsBlocked() {
        Screening result = check.run(applicant("John Doe", "LY-1", "GB"));
        assertThat(result.decision()).isEqualTo(ScreeningDecision.BLOCKED);
        assertThat(result.riskScore()).isEqualTo(100);
    }

    @Test
    void ordinaryApplicantInLowRiskCountryIsCleared() {
        Screening result = check.run(applicant("Ada Lovelace", "LY-1001", "GB"));
        assertThat(result.decision()).isEqualTo(ScreeningDecision.CLEARED);
        assertThat(result.riskScore()).isLessThan(50);
    }

    @Test
    void highRiskJurisdictionNeedsReview() {
        Screening result = check.run(applicant("Ada Lovelace", "LY-1001", "KP"));
        assertThat(result.decision()).isEqualTo(ScreeningDecision.REVIEW);
        assertThat(result.riskScore()).isGreaterThanOrEqualTo(70);
    }
}
```

### `src/test/java/.../ScreeningStoreTest.java`

```java
package com.databytes.screening;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScreeningStoreTest {

    @Test
    void marksAnEventProcessedExactlyOnce() {
        ScreeningStore store = new ScreeningStore();

        assertThat(store.markProcessed("evt-1")).isTrue();
        assertThat(store.markProcessed("evt-1")).isFalse();
        assertThat(store.processedCount()).isEqualTo(1);
    }

    @Test
    void keepsMostRecentScreeningFirst() {
        ScreeningStore store = new ScreeningStore();
        store.record(new Screening(1L, "First", "n1", ScreeningDecision.CLEARED, 10, "ok", Instant.now()));
        store.record(new Screening(2L, "Second", "n2", ScreeningDecision.REVIEW, 80, "review", Instant.now()));

        assertThat(store.recent()).first()
                .extracting(Screening::applicantId)
                .isEqualTo(2L);
    }
}
```

### `src/test/java/.../ScreeningConsumerTest.java` — drives the consumer, asserts it never pins

```java
package com.databytes.screening;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.virtual.ShouldNotPin;
import io.quarkus.test.junit.virtual.VirtualThreadUnit;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Drives the consumer through the in-memory connector (see %test config) and
 * asserts the event becomes a screening result. No broker required; the real
 * Kafka path is exercised by the Compose end-to-end.
 *
 * <p>{@code @VirtualThreadUnit @ShouldNotPin} fails the test if the
 * {@code @RunOnVirtualThread} consumer ever pins its carrier thread — the
 * guard that keeps the virtual-thread claim honest as the handler grows.
 */
@QuarkusTest
@VirtualThreadUnit
@ShouldNotPin
class ScreeningConsumerTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    ScreeningStore store;

    @Test
    void anApplicantRegisteredEventBecomesAScreening() throws InterruptedException {
        InMemorySource<String> source = connector.source("applicant-registered");
        int before = store.recent().size();

        source.send("""
                {"applicantId":7,"fullName":"Ada Lovelace","nationalId":"LY-7","country":"GB",
                 "email":"ada@example.com","status":"PENDING",
                 "occurredAt":"2026-01-01T00:00:00Z","correlationId":"corr-7"}
                """);

        long deadline = System.currentTimeMillis() + 10_000;
        while (store.recent().size() <= before && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        assertThat(store.recent()).hasSizeGreaterThan(before);
        Screening latest = store.recent().getFirst();
        assertThat(latest.applicantId()).isEqualTo(7L);
        assertThat(latest.decision()).isEqualTo(ScreeningDecision.CLEARED);
    }
}
```

## That's the capstone

You've now copied and run the whole reference platform. `kyc-service` is exactly as it was on Day 9;
`screening-service` is the new half that reacts to it. To rebuild any piece yourself, each day's
[`README.md`](./README.md) walks it from its checkpoint.
