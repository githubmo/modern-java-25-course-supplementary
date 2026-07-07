# Day 9 — Copy-along: the KYC Service on PostgreSQL

> For anyone who wants to **see how persistence slots in** without wiring it by hand. Every file that
> changed or appeared since Day 8 is below, ready to copy.
>
> Prefer the challenge? Do [`README.md`](./README.md) instead.

## Get the code

**Fastest — check out the finished day:**

```bash
git switch --detach checkpoint/day-09
cd apps/kyc-service
```

Back to your branch later: `git switch -`.

**Or — continue your Day 8 project:** add the extensions, then paste the changed/new files below.

```bash
quarkus extension add jdbc-postgresql hibernate-orm-panache flyway messaging-kafka scheduler \
  opentelemetry micrometer-registry-prometheus logging-json
```

Everything from Day 8 that isn't listed under "Changed" below is **unchanged** — keep it as is.

> **What's in this checkpoint.** Day 9's story is *persistence* — an applicant survives a restart. But
> the checkpoint also lands the **transactional outbox**, the Kafka producer config, and the
> observability plumbing. They're wired but dormant: there's no broker and no consumer until **Day 10**.
> Copy them now; they come alive next day. If you only care about the database, read the first five
> files and skip the `outbox/` + `observability/` packages for now.

## Run it

**Docker must be running** — Quarkus Dev Services starts a throwaway Postgres; there's no connection
string to configure.

```bash
./mvnw quarkus:dev
```

Prove it persists (the whole point):

```bash
curl -i -X POST localhost:8080/applicants -H 'Content-Type: application/json' \
  -d '{"fullName":"Ada Lovelace","nationalId":"LY-1001","dateOfBirth":"1990-05-20","country":"GB","email":"ada@example.com"}'
```

Note the id in the `Location` header, stop dev mode (Ctrl-C), start it again, `GET /applicants/{id}` —
the applicant is still there. Tests: `./mvnw -B verify` (they get their own Dev Services Postgres).

---

## Changed since Day 8

### `pom.xml` — added extensions

Adds Panache + PostgreSQL + Flyway (persistence), Kafka + Scheduler (outbox relay), OpenTelemetry +
Micrometer + JSON logging (observability), and two test deps (Mockito, in-memory messaging).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.databytes</groupId>
    <artifactId>kyc-service</artifactId>
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
            <artifactId>quarkus-hibernate-validator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-hibernate-orm-panache</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-jdbc-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-messaging-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-scheduler</artifactId>
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
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5-mockito</artifactId>
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

### `src/main/resources/application.properties` — persistence, messaging, observability, profiles

Note: **no JDBC URL** for dev/test — the blank URL is what triggers Dev Services. Flyway owns the
schema; Hibernate only validates.

```properties
## ---------------------------------------------------------------------------
## KYC Service configuration
##
## Dev and test rely on Quarkus Dev Services: Postgres and Kafka containers are
## started automatically, so there is nothing to configure for them here. Prod
## reads connection details from the environment (see infra/compose.yaml).
## ---------------------------------------------------------------------------

quarkus.application.name=kyc-service
quarkus.http.port=8080

## --- Persistence -----------------------------------------------------------
## Flyway owns the schema; Hibernate only validates that the entities match it.
quarkus.datasource.db-kind=postgresql
quarkus.hibernate-orm.database.generation=none
quarkus.flyway.migrate-at-start=true
## Use a dedicated default JSON mapper for jsonb columns, independent of the
## REST ObjectMapper (Quarkus guards against accidentally sharing the two).
quarkus.hibernate-orm.mapping.format.global=ignore

## --- Messaging: outgoing `applicant-registered` events -> Kafka -------------
mp.messaging.outgoing.applicant-registered.connector=smallrye-kafka
mp.messaging.outgoing.applicant-registered.topic=applicant-registered
mp.messaging.outgoing.applicant-registered.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.applicant-registered.value.serializer=org.apache.kafka.common.serialization.StringSerializer

## --- Transactional outbox poller ------------------------------------------
outbox.poll-interval=2s
outbox.batch-size=50

## --- Observability ---------------------------------------------------------
quarkus.micrometer.export.prometheus.enabled=true
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317

## --- Dev profile -----------------------------------------------------------
## Human-readable logs; tracing SDK off so we don't need a collector locally.
%dev.quarkus.log.console.json=false
%dev.quarkus.otel.sdk.disabled=true

## --- Test profile ----------------------------------------------------------
%test.quarkus.log.console.json=false
%test.quarkus.otel.sdk.disabled=true
## Random test HTTP port so the tests never clash with running infra
## (kafka-ui uses 8081, the conventional Quarkus test port). REST Assured
## discovers the chosen port automatically.
%test.quarkus.http.test-port=0
## Swap the Kafka connector for an in-memory one: tests assert on the published
## event without a broker, and Postgres still runs for real via Dev Services.
%test.mp.messaging.outgoing.applicant-registered.connector=smallrye-in-memory
%test.quarkus.kafka.devservices.enabled=false

## --- Prod profile ----------------------------------------------------------
## Structured JSON logs, real datasource + broker, traces to the collector.
%prod.quarkus.log.console.json=true
%prod.quarkus.datasource.jdbc.url=${QUARKUS_DATASOURCE_JDBC_URL:jdbc:postgresql://postgres:5432/orders}
%prod.quarkus.datasource.username=${POSTGRES_USER:orders}
%prod.quarkus.datasource.password=${POSTGRES_PASSWORD:orders}
%prod.kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
%prod.quarkus.otel.sdk.disabled=false
%prod.quarkus.otel.exporter.otlp.endpoint=${QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT:http://otel-collector:4317}
```

### `domain/Applicant.java` — now a JPA entity

Same fields as Day 8, plus the persistence annotations: `@Entity`, `@GeneratedValue(IDENTITY)`,
`@Enumerated(STRING)`, and `@JdbcTypeCode(SqlTypes.JSON)` for the `jsonb` column.

```java
package com.databytes.kyc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The applicant aggregate — a person onboarding to the bank. Fields are public —
 * the idiomatic Quarkus style — and the repository ({@link ApplicantRepository})
 * owns all access so the persistence concern stays testable. A newly registered
 * applicant starts {@code PENDING}; the screening service decides the outcome.
 */
@Entity
@Table(name = "applicants")
public class Applicant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "full_name", nullable = false)
    public String fullName;

    @Column(name = "national_id", nullable = false)
    public String nationalId;

    @Column(name = "date_of_birth", nullable = false)
    public LocalDate dateOfBirth;

    @Column(nullable = false)
    public String country;

    @Column(nullable = false)
    public String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ApplicantStatus status = ApplicantStatus.PENDING;

    /** Free-form, schemaless KYC attributes — stored in a Postgres {@code jsonb} column. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public Map<String, String> attributes = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Age in whole years on a given date — derived, never stored. */
    public int ageOn(LocalDate asOf) {
        return Period.between(dateOfBirth, asOf).getYears();
    }

    /** A bank can only onboard adults — a simple derived KYC rule. */
    public boolean isAdult() {
        return ageOn(LocalDate.now()) >= 18;
    }
}
```

### `application/ApplicantService.java` — the in-memory `Map` becomes the DB + outbox

The public methods are unchanged, so `ApplicantResource` doesn't move. `register` now persists the
applicant **and** its outbox event in one `@Transactional` method — both commit or neither does.

```java
package com.databytes.kyc.application;

import com.databytes.kyc.domain.Applicant;
import com.databytes.kyc.domain.ApplicantRepository;
import com.databytes.kyc.domain.ApplicantStatus;
import com.databytes.kyc.events.ApplicantRegisteredEvent;
import com.databytes.kyc.observability.TraceContextSupport;
import com.databytes.kyc.outbox.OutboxEvent;
import com.databytes.kyc.outbox.OutboxRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jboss.logging.MDC;

/**
 * Application service for applicants. The write path is the heart of the
 * reference app: persisting the applicant and writing its event to the outbox
 * happen in one transaction, so they either both commit or both roll back.
 * Nothing is sent to Kafka here — the {@code OutboxDispatcher} relays committed
 * events afterwards, and the screening service runs the background check.
 */
@ApplicationScoped
public class ApplicantService {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    private final ApplicantRepository applicants;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final TraceContextSupport traces;
    private final MeterRegistry meters;

    @Inject
    public ApplicantService(ApplicantRepository applicants, OutboxRepository outbox, ObjectMapper mapper,
                            TraceContextSupport traces, MeterRegistry meters) {
        this.applicants = applicants;
        this.outbox = outbox;
        this.mapper = mapper;
        this.traces = traces;
        this.meters = meters;
    }

    @Transactional
    public Applicant register(Applicant draft) {
        draft.status = ApplicantStatus.PENDING;
        applicants.persist(draft); // IDENTITY key: inserted immediately, draft.id is now set

        ApplicantRegisteredEvent event = new ApplicantRegisteredEvent(
                draft.id, draft.fullName, draft.nationalId, draft.country,
                draft.email, draft.status.name(), Instant.now(), currentCorrelationId());

        OutboxEvent record = new OutboxEvent();
        record.aggregateType = "applicant";
        record.aggregateId = String.valueOf(draft.id);
        record.type = "applicant-registered";
        record.payload = mapper.convertValue(event, JSON_OBJECT);
        record.traceParent = traces.captureTraceParent();
        outbox.persist(record);

        meters.counter("applicants.registered").increment();
        return draft;
    }

    @Transactional
    public Applicant findById(Long id) {
        Applicant applicant = applicants.findById(id);
        if (applicant == null) {
            throw new ApplicantNotFoundException(id);
        }
        return applicant;
    }

    @Transactional
    public List<Applicant> list(int page, int size) {
        return applicants.listNewest(page, size);
    }

    private static String currentCorrelationId() {
        Object id = MDC.get("correlationId");
        return id == null ? null : id.toString();
    }
}
```

---

## New files

### `domain/ApplicantRepository.java`

Panache repository — `persist`/`findById` are inherited; only `listNewest` is hand-written.

```java
package com.databytes.kyc.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Panache repository for {@link Applicant}. Using the repository pattern (rather
 * than active record) keeps the entity free of static persistence calls, which
 * makes the service layer straightforward to unit-test with a mock.
 */
@ApplicationScoped
public class ApplicantRepository implements PanacheRepository<Applicant> {

    public List<Applicant> listNewest(int page, int size) {
        return findAll(Sort.by("createdAt").descending())
                .page(Page.of(page, size))
                .list();
    }
}
```

### `src/main/resources/db/migration/V1__create_applicants.sql`

The filename *is* the version.

```sql
-- Day 9: the applicants table. Flyway owns the schema; Hibernate only validates
-- that the Applicant entity matches it. The `attributes` column is jsonb, so
-- schemaless per-applicant KYC data lives alongside the typed columns and stays queryable.

create table applicants (
    id            bigint generated always as identity primary key,
    full_name     varchar(128)  not null,
    national_id   varchar(64)   not null,
    date_of_birth date          not null,
    country       varchar(64)   not null,
    email         varchar(256)  not null,
    status        varchar(16)   not null,
    attributes    jsonb         not null default '{}'::jsonb,
    created_at    timestamptz   not null default now(),
    updated_at    timestamptz   not null default now()
);
```

### `src/main/resources/db/migration/V2__create_outbox.sql`

```sql
-- Day 9: the transactional outbox.
--
-- Written in the same transaction as the applicant it describes, so an applicant and its
-- "applicant-registered" event are persisted atomically. A polling dispatcher relays
-- PENDING rows to Kafka and marks them PROCESSED. `trace_parent` carries the W3C
-- trace context so the asynchronous dispatch can continue the original trace.

create table outbox_event (
    id             uuid          primary key,
    aggregate_type varchar(64)   not null,
    aggregate_id   varchar(64)   not null,
    type           varchar(64)   not null,
    payload        jsonb         not null,
    trace_parent   varchar(128),
    status         varchar(16)   not null default 'PENDING',
    created_at     timestamptz   not null default now(),
    processed_at   timestamptz
);

-- The poller only ever scans PENDING rows; a partial index keeps that cheap.
create index idx_outbox_pending on outbox_event (created_at) where status = 'PENDING';
```

### `events/ApplicantRegisteredEvent.java`

The topic contract, as an immutable record.

```java
package com.databytes.kyc.events;

import java.time.Instant;

/**
 * The contract published to Kafka when an applicant registers. It is a record so
 * it is immutable and serialises to a flat JSON object. The Screening Service
 * holds its own copy of this shape — the topic is the contract, not a shared class.
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

### `outbox/OutboxEvent.java`

```java
package com.databytes.kyc.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A pending domain event, written in the same transaction as the aggregate it
 * describes. The {@code payload} is stored as {@code jsonb} so it is queryable
 * in the database and emitted verbatim to Kafka by the dispatcher.
 *
 * <p>{@code traceParent} captures the W3C trace context at creation time so the
 * asynchronous dispatch can continue the original request's distributed trace.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    public UUID id;

    @Column(name = "aggregate_type", nullable = false)
    public String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    public String aggregateId;

    @Column(nullable = false)
    public String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public Map<String, Object> payload = new HashMap<>();

    @Column(name = "trace_parent")
    public String traceParent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "processed_at")
    public Instant processedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
```

### `outbox/OutboxStatus.java`

```java
package com.databytes.kyc.outbox;

/** Delivery state of an outbox row as it is relayed to Kafka. */
public enum OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED
}
```

### `outbox/OutboxRepository.java`

```java
package com.databytes.kyc.outbox;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

/** Panache repository for {@link OutboxEvent} (UUID-keyed). */
@ApplicationScoped
public class OutboxRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {

    /** Oldest-first batch of events still waiting to be relayed to Kafka. */
    public List<OutboxEvent> findPending(int batchSize) {
        return find("status", Sort.by("createdAt").ascending(), OutboxStatus.PENDING)
                .page(Page.ofSize(batchSize))
                .list();
    }

    public long countPending() {
        return count("status", OutboxStatus.PENDING);
    }
}
```

### `outbox/OutboxStore.java`

The transactional boundary — kept separate from the dispatcher so `@Transactional` (a CDI interceptor)
actually fires, and so the slow Kafka send stays outside any open DB transaction.

```java
package com.databytes.kyc.outbox;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional boundary for the dispatcher's database work. It is a separate
 * bean from {@code OutboxDispatcher} on purpose: {@code @Transactional} is a CDI
 * interceptor, so each call has to cross a bean boundary to take effect. Keeping
 * these methods here also keeps the Kafka send (slow I/O) outside any open
 * database transaction.
 */
@ApplicationScoped
public class OutboxStore {

    @Inject
    OutboxRepository outbox;

    @Transactional
    public List<OutboxEvent> nextBatch(int size) {
        return outbox.findPending(size);
    }

    @Transactional
    public void markProcessed(UUID id) {
        outbox.findByIdOptional(id).ifPresent(event -> {
            event.status = OutboxStatus.PROCESSED;
            event.processedAt = Instant.now();
        });
    }

    @Transactional
    public void markFailed(UUID id) {
        outbox.findByIdOptional(id).ifPresent(event -> event.status = OutboxStatus.FAILED);
    }
}
```

### `outbox/OutboxDispatcher.java`

The scheduled poller that relays committed rows to Kafka — keyed by applicant id, with an `event-id`
header for the consumer to dedupe on. (Dormant until Day 10 brings up a broker.)

```java
package com.databytes.kyc.outbox;

import com.databytes.kyc.observability.TraceContextSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.context.Scope;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Relays committed outbox rows to Kafka — the "polling publisher" half of the
 * transactional outbox. One scheduler instance polls and {@code SKIP} prevents
 * overlapping runs; multiple replicas would instead claim rows with
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}. Delivery is at-least-once (a crash
 * between send and mark re-sends), so each message carries a stable
 * {@code event-id} the consumer dedupes on.
 */
@ApplicationScoped
public class OutboxDispatcher {

    private static final Logger LOG = Logger.getLogger(OutboxDispatcher.class);

    private final OutboxStore store;
    private final ObjectMapper mapper;
    private final TraceContextSupport traces;
    private final MeterRegistry meters;
    private final MutinyEmitter<String> emitter;
    private final int batchSize;

    @Inject
    public OutboxDispatcher(OutboxStore store, ObjectMapper mapper, TraceContextSupport traces,
                            MeterRegistry meters,
                            @Channel("applicant-registered") MutinyEmitter<String> emitter,
                            @ConfigProperty(name = "outbox.batch-size", defaultValue = "50") int batchSize) {
        this.store = store;
        this.mapper = mapper;
        this.traces = traces;
        this.meters = meters;
        this.emitter = emitter;
        this.batchSize = batchSize;
    }

    @Scheduled(every = "{outbox.poll-interval}", concurrentExecution = ConcurrentExecution.SKIP)
    void dispatch() {
        for (OutboxEvent event : store.nextBatch(batchSize)) {
            relay(event);
        }
    }

    private void relay(OutboxEvent event) {
        try {
            String value = mapper.writeValueAsString(event.payload);

            RecordHeaders headers = new RecordHeaders();
            headers.add("event-id", event.id.toString().getBytes(StandardCharsets.UTF_8));
            headers.add("event-type", event.type.getBytes(StandardCharsets.UTF_8));

            Message<String> message = Message.of(value).addMetadata(
                    OutgoingKafkaRecordMetadata.<String>builder()
                            .withKey(event.aggregateId)
                            .withHeaders(headers)
                            .build());

            // Continue the trace that registered the applicant, even though we are now on
            // a scheduler thread far from the original HTTP request.
            try (Scope ignored = traces.restore(event.traceParent).makeCurrent()) {
                emitter.sendMessage(message).await().indefinitely();
            }

            store.markProcessed(event.id);
            meters.counter("outbox.dispatched").increment();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to relay outbox event %s", event.id);
            store.markFailed(event.id);
            meters.counter("outbox.failed").increment();
        }
    }
}
```

### `observability/TraceContextSupport.java`

Serialises the active trace onto the outbox row and rebuilds it at dispatch time, so the async relay
joins the original request's trace.

```java
package com.databytes.kyc.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the synchronous request trace and the asynchronous outbox dispatch.
 *
 * <p>At registration time {@link #captureTraceParent()} serialises the active
 * span into a W3C {@code traceparent} string that is stored on the outbox row.
 * When the dispatcher later relays the event it calls {@link #restore(String)}
 * to rebuild that context and makes it current, so the Kafka producer span — and
 * therefore the Screening Service consumer span — joins the original trace.
 */
@ApplicationScoped
public class TraceContextSupport {

    private static final String TRACEPARENT = "traceparent";

    private static final TextMapSetter<Map<String, String>> SETTER =
            (carrier, key, value) -> {
                if (carrier != null) {
                    carrier.put(key, value);
                }
            };

    private static final TextMapGetter<Map<String, String>> GETTER =
            new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(Map<String, String> carrier) {
                    return carrier.keySet();
                }

                @Override
                public String get(Map<String, String> carrier, String key) {
                    return carrier == null ? null : carrier.get(key);
                }
            };

    @Inject
    OpenTelemetry openTelemetry;

    /** The current trace as a {@code traceparent} header value, or {@code null} if untraced. */
    public String captureTraceParent() {
        Map<String, String> carrier = new HashMap<>();
        openTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), carrier, SETTER);
        return carrier.get(TRACEPARENT);
    }

    /** Rebuild an OpenTelemetry {@link Context} from a stored {@code traceparent}. */
    public Context restore(String traceParent) {
        if (traceParent == null || traceParent.isBlank()) {
            return Context.current();
        }
        Map<String, String> carrier = Map.of(TRACEPARENT, traceParent);
        return openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), carrier, GETTER);
    }
}
```

### `observability/CorrelationIdFilter.java`

```java
package com.databytes.kyc.observability;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import org.jboss.logging.MDC;

/**
 * Ensures every request carries a correlation id. Reads {@code X-Correlation-Id}
 * (or mints one), puts it in the MDC so it appears on every JSON log line, and
 * echoes it back on the response. The registration path copies the id into the
 * published event, so the Screening Service can log under the same id.
 */
@Provider
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String id = requestContext.getHeaderString(HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, id);
        requestContext.setProperty(MDC_KEY, id);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object id = requestContext.getProperty(MDC_KEY);
        if (id != null) {
            responseContext.getHeaders().putSingle(HEADER, id);
        }
        MDC.remove(MDC_KEY);
    }
}
```

### `observability/OutboxBacklogHealthCheck.java`

```java
package com.databytes.kyc.observability;

import com.databytes.kyc.outbox.OutboxRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness check that fails if the outbox backlog grows unbounded — a sign the
 * dispatcher or Kafka is unhealthy. Complements the datasource and Kafka checks
 * that the extensions register automatically.
 */
@Readiness
@ApplicationScoped
public class OutboxBacklogHealthCheck implements HealthCheck {

    private static final long MAX_BACKLOG = 1000;

    @Inject
    OutboxRepository outbox;

    @Override
    @Transactional
    public HealthCheckResponse call() {
        long pending = outbox.countPending();
        return HealthCheckResponse.named("outbox-backlog")
                .status(pending < MAX_BACKLOG)
                .withData("pending", pending)
                .build();
    }
}
```

---

## Tests

### `src/test/java/.../application/ApplicantServiceTest.java` — changed (now mocks the repository)

```java
package com.databytes.kyc.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.databytes.kyc.domain.Applicant;
import com.databytes.kyc.domain.ApplicantRepository;
import com.databytes.kyc.domain.ApplicantStatus;
import com.databytes.kyc.observability.TraceContextSupport;
import com.databytes.kyc.outbox.OutboxEvent;
import com.databytes.kyc.outbox.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pure unit test (no Quarkus container): proves the write path persists the
 * applicant and the outbox event together, which is the contract the rest of the
 * system relies on. Repositories are mocked; the real Jackson mapper builds the
 * payload so we exercise serialisation too.
 */
class ApplicantServiceTest {

    private final ApplicantRepository applicants = mock(ApplicantRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final TraceContextSupport traces = mock(TraceContextSupport.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ApplicantService service =
            new ApplicantService(applicants, outbox, mapper, traces, meters);

    @Test
    void registerPersistsApplicantAndOutboxEventAtomically() {
        // Simulate the IDENTITY key being assigned on persist.
        doAnswer(invocation -> {
            Applicant persisted = invocation.getArgument(0);
            persisted.id = 42L;
            return null;
        }).when(applicants).persist(any(Applicant.class));

        Applicant draft = new Applicant();
        draft.fullName = "Ada Lovelace";
        draft.nationalId = "LY-1001";
        draft.dateOfBirth = LocalDate.of(1990, 5, 20);
        draft.country = "GB";
        draft.email = "ada@example.com";

        Applicant saved = service.register(draft);

        assertThat(saved.id).isEqualTo(42L);
        assertThat(saved.status).isEqualTo(ApplicantStatus.PENDING);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).persist(captor.capture());
        OutboxEvent event = captor.getValue();

        assertThat(event.type).isEqualTo("applicant-registered");
        assertThat(event.aggregateType).isEqualTo("applicant");
        assertThat(event.aggregateId).isEqualTo("42");
        assertThat(event.payload)
                .containsEntry("fullName", "Ada Lovelace")
                .containsEntry("nationalId", "LY-1001")
                .containsEntry("country", "GB")
                .containsKeys("applicantId", "email", "status");
        assertThat(meters.counter("applicants.registered").count()).isEqualTo(1.0);
    }
}
```

### `src/test/java/.../outbox/ApplicantEventPublishingTest.java` — new (in-memory connector, no broker)

```java
package com.databytes.kyc.outbox;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the outbox -> Kafka relay. The Kafka connector is swapped
 * for an in-memory one (see %test config), so we can assert the published event
 * deterministically without a broker. Postgres still runs for real.
 */
@QuarkusTest
class ApplicantEventPublishingTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    void registeringAnApplicantPublishesAnApplicantRegisteredEvent() throws InterruptedException {
        InMemorySink<String> sink = connector.sink("applicant-registered");
        int before = sink.received().size();

        given().contentType(MediaType.APPLICATION_JSON)
                .body("""
                        { "fullName": "Grace Hopper", "nationalId": "LY-9", "dateOfBirth": "1985-12-09",
                          "country": "US", "email": "grace@example.com" }
                        """)
                .when().post("/applicants")
                .then().statusCode(201);

        // The outbox poller relays on a 2s schedule; wait for it to catch up.
        long deadline = System.currentTimeMillis() + 15_000;
        while (sink.received().size() <= before && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }

        assertThat(sink.received()).hasSizeGreaterThan(before);
        Message<String> last = sink.received().get(sink.received().size() - 1);
        assertThat(last.getPayload()).contains("\"nationalId\":\"LY-9\"").contains("\"applicantId\"");
    }
}
```

## Unchanged from Day 8

Keep these exactly as they were: `domain/ApplicantStatus.java`, `application/ApplicantNotFoundException.java`,
all of `api/` (`ApplicantResource`, `ApplicantResponse`, `RegisterApplicantRequest`, `ApiError`,
`ValidationExceptionMapper`, `ApplicantNotFoundExceptionMapper`), and the tests `domain/ApplicantTest.java`
and `api/ApplicantResourceTest.java`.

## Then move on

Day 10 adds the **second service** that reacts to these applicants:
[`../day-10/COPY-ALONG.md`](../day-10/COPY-ALONG.md).
