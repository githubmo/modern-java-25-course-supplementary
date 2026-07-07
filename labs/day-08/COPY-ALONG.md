# Day 8 — Copy-along: the finished KYC Service

> For anyone who wants to **see how it all fits** without building it from scratch. Every file is below,
> ready to copy. No typing the app out from the slides.
>
> Prefer the challenge? Do [`README.md`](./README.md) instead — this is the shortcut, not the lesson.

## Two ways to get the code

**Fastest — check out the finished day (gets every file, including the Maven wrapper and Dockerfile):**

```bash
git switch --detach checkpoint/day-08
cd apps/kyc-service
```

Back to your branch later: `git switch -`. (Keep `main` open too? `git worktree add ../kyc-day-08 checkpoint/day-08`.)

**Or — start an empty Quarkus project and paste the files below into it:**

```bash
quarkus create app com.databytes:kyc-service \
  --extension=rest-jackson,hibernate-validator,smallrye-health
cd kyc-service
```

That scaffold gives you the `pom.xml`, `mvnw` wrapper, `.gitignore`, and `Dockerfile` for free. Then
replace `pom.xml` / `application.properties` and add the Java files below. (The `.mvn/`, `mvnw`,
`.gitignore`, `.dockerignore`, and `Dockerfile` are generated scaffolding — nothing to hand-copy.)

## Run it

No database, no Docker today — it runs on its own:

```bash
./mvnw quarkus:dev          # or: quarkus dev
```

```bash
# register an applicant -> 201 with a Location header
curl -i -X POST localhost:8080/applicants -H 'Content-Type: application/json' \
  -d '{"fullName":"Ada Lovelace","nationalId":"LY-1001","dateOfBirth":"1990-05-20","country":"GB","email":"ada@example.com"}'
curl -i -X POST localhost:8080/applicants -H 'Content-Type: application/json' -d '{}'   # -> 400, lists fields
curl -i localhost:8080/applicants/999                                                    # -> 404
curl localhost:8080/q/health                                                             # -> UP
```

Dev UI: <http://localhost:8080/q/dev/>. Tests: `./mvnw -B verify`.

## Reading order

The request flows **`ApplicantResource` → `ApplicantService` → in-memory `Map`**, DTOs on the edge,
exception mappers turning failures into a uniform JSON body. The **one seam that matters**: the store
sits behind DI, so Day 9 swaps it for a database and the REST layer above never changes.

---

## The files

### `pom.xml`

Four runtime extensions (REST+Jackson, Bean Validation, Health, ArC) and test deps only — no database,
no Kafka.

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

One key, three environments — the config-profile demo.

```properties
## ---------------------------------------------------------------------------
## KYC Service configuration
##
## Day 8: an in-memory web service — no database and no Kafka yet. Those arrive
## on Day 9 (persistence) and Day 10 (messaging). Config profiles below show how
## the same key takes a different value per environment.
## ---------------------------------------------------------------------------

quarkus.application.name=kyc-service
quarkus.http.port=8080

## --- Config profiles: one key, three environments -------------------------
kyc.onboarding.message=Welcome to Data Bytes Bank
%dev.kyc.onboarding.message=Welcome (dev)
%prod.kyc.onboarding.message=Welcome to Data Bytes Bank

## Random test HTTP port so the tests never clash with a running dev instance.
## REST Assured discovers the chosen port automatically.
%test.quarkus.http.test-port=0
```

### `domain/Applicant.java`

The aggregate — a plain object today (no persistence annotations), with a derived KYC rule.

```java
package com.databytes.kyc.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

/**
 * The applicant aggregate — a person onboarding to the bank. Today it is a plain
 * in-memory object held by {@link com.databytes.kyc.application.ApplicantService};
 * on Day 9 it becomes a JPA entity backed by PostgreSQL without the REST layer
 * above it changing. A newly registered applicant starts {@code PENDING}; the
 * screening service (Day 10) decides the outcome.
 */
public class Applicant {

    public Long id;
    public String fullName;
    public String nationalId;
    public LocalDate dateOfBirth;
    public String country;
    public String email;
    public ApplicantStatus status = ApplicantStatus.PENDING;

    /** Free-form, schemaless KYC attributes. */
    public Map<String, String> attributes = new HashMap<>();

    public Instant createdAt;

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

### `domain/ApplicantStatus.java`

```java
package com.databytes.kyc.domain;

/** KYC lifecycle states of an applicant. Set by the screening outcome. */
public enum ApplicantStatus {
    /** Registered; background check not yet completed. */
    PENDING,
    /** Background check passed — the applicant can be onboarded. */
    CLEARED,
    /** Flagged for manual review by a compliance officer. */
    REVIEW,
    /** Failed the background check (e.g. watchlist match) — onboarding refused. */
    BLOCKED
}
```

### `application/ApplicantService.java` — **the seam**

In-memory today; injected behind `ApplicantResource`. Day 9 replaces the body and nothing above changes.

```java
package com.databytes.kyc.application;

import com.databytes.kyc.domain.Applicant;
import com.databytes.kyc.domain.ApplicantStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Application service for applicants. Today it holds applicants in memory — the
 * focus of Day 8 is the framework, DI, and the HTTP layer, not persistence.
 * Because {@code ApplicantResource} depends on this class (not on a {@code Map}),
 * Day 9 swaps this in-memory store for a real repository without the REST layer
 * changing a line.
 */
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

    public Applicant findById(Long id) {
        Applicant applicant = store.get(id);
        if (applicant == null) {
            throw new ApplicantNotFoundException(id);
        }
        return applicant;
    }

    public List<Applicant> list(int page, int size) {
        return store.values().stream()
                .sorted(Comparator.comparing((Applicant a) -> a.createdAt).reversed())
                .skip((long) page * size)
                .limit(size)
                .toList();
    }
}
```

### `application/ApplicantNotFoundException.java`

```java
package com.databytes.kyc.application;

/** Thrown when an applicant id does not resolve; mapped to HTTP 404 in the API layer. */
public class ApplicantNotFoundException extends RuntimeException {

    public ApplicantNotFoundException(Long id) {
        super("Applicant " + id + " was not found");
    }
}
```

### `api/RegisterApplicantRequest.java`

Inbound DTO. Bean Validation lives here, so bad input is rejected before any logic runs.

```java
package com.databytes.kyc.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import java.util.Map;

/**
 * Inbound applicant registration. Bean Validation annotations on the record
 * components are enforced by the {@code @Valid} parameter on the resource;
 * failures are turned into a clean 400 by {@code ValidationExceptionMapper}.
 */
public record RegisterApplicantRequest(
        @NotBlank String fullName,
        @NotBlank String nationalId,
        @NotNull @Past LocalDate dateOfBirth,
        @NotBlank String country,
        @NotBlank @Email String email,
        Map<String, String> attributes) {
}
```

### `api/ApplicantResponse.java`

Outbound DTO — the domain object never leaks over HTTP.

```java
package com.databytes.kyc.api;

import com.databytes.kyc.domain.Applicant;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** Outbound applicant representation. Keeps the entity out of the HTTP layer. */
public record ApplicantResponse(
        Long id,
        String fullName,
        String nationalId,
        LocalDate dateOfBirth,
        String country,
        String email,
        String status,
        Map<String, String> attributes,
        Instant createdAt) {

    public static ApplicantResponse from(Applicant applicant) {
        return new ApplicantResponse(
                applicant.id, applicant.fullName, applicant.nationalId, applicant.dateOfBirth,
                applicant.country, applicant.email, applicant.status.name(),
                applicant.attributes, applicant.createdAt);
    }
}
```

### `api/ApplicantResource.java` — the HTTP layer

Injects `ApplicantService`, not a `Map`. `@Valid` triggers validation; `201` + `Location` on create.

```java
package com.databytes.kyc.api;

import com.databytes.kyc.application.ApplicantService;
import com.databytes.kyc.domain.Applicant;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.HashMap;
import java.util.List;

@Path("/applicants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApplicantResource {

    @Inject
    ApplicantService service;

    @POST
    public Response register(@Valid RegisterApplicantRequest request, @Context UriInfo uriInfo) {
        Applicant draft = new Applicant();
        draft.fullName = request.fullName();
        draft.nationalId = request.nationalId();
        draft.dateOfBirth = request.dateOfBirth();
        draft.country = request.country();
        draft.email = request.email();
        if (request.attributes() != null) {
            draft.attributes = new HashMap<>(request.attributes());
        }
        Applicant saved = service.register(draft);
        URI location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(saved.id)).build();
        return Response.created(location).entity(ApplicantResponse.from(saved)).build();
    }

    @GET
    @Path("/{id}")
    public ApplicantResponse get(@PathParam("id") Long id) {
        return ApplicantResponse.from(service.findById(id));
    }

    @GET
    public List<ApplicantResponse> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return service.list(page, size).stream().map(ApplicantResponse::from).toList();
    }
}
```

### `api/ApiError.java`

```java
package com.databytes.kyc.api;

import java.time.Instant;
import java.util.List;

/** Uniform error body returned by the exception mappers. */
public record ApiError(
        int status,
        String error,
        String message,
        List<FieldViolation> violations,
        Instant timestamp) {

    public record FieldViolation(String field, String message) {}

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, List.of(), Instant.now());
    }
}
```

### `api/ValidationExceptionMapper.java`

```java
package com.databytes.kyc.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.List;

/** Turns Bean Validation failures into a 400 with a per-field breakdown. */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ApiError.FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(lastNode(v), v.getMessage()))
                .toList();
        ApiError error = new ApiError(
                Response.Status.BAD_REQUEST.getStatusCode(),
                "Bad Request",
                "Request validation failed",
                violations,
                Instant.now());
        return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
```

### `api/ApplicantNotFoundExceptionMapper.java`

```java
package com.databytes.kyc.api;

import com.databytes.kyc.application.ApplicantNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Maps a missing applicant to a 404 with the standard {@link ApiError} body. */
@Provider
public class ApplicantNotFoundExceptionMapper implements ExceptionMapper<ApplicantNotFoundException> {

    @Override
    public Response toResponse(ApplicantNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ApiError.of(
                        Response.Status.NOT_FOUND.getStatusCode(),
                        "Not Found",
                        exception.getMessage()))
                .build();
    }
}
```

---

## The tests (make `./mvnw -B verify` green)

### `src/test/java/.../domain/ApplicantTest.java`

```java
package com.databytes.kyc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ApplicantTest {

    @Test
    void ageOnIsWholeYearsBetweenBirthAndDate() {
        Applicant applicant = new Applicant();
        applicant.dateOfBirth = LocalDate.of(2000, 1, 1);

        assertThat(applicant.ageOn(LocalDate.of(2020, 1, 1))).isEqualTo(20);
        assertThat(applicant.ageOn(LocalDate.of(2019, 12, 31))).isEqualTo(19);
    }

    @Test
    void isAdultReflectsTheEighteenYearRule() {
        Applicant adult = new Applicant();
        adult.dateOfBirth = LocalDate.now().minusYears(30);
        assertThat(adult.isAdult()).isTrue();

        Applicant minor = new Applicant();
        minor.dateOfBirth = LocalDate.now().minusYears(10);
        assertThat(minor.isAdult()).isFalse();
    }
}
```

### `src/test/java/.../application/ApplicantServiceTest.java`

```java
package com.databytes.kyc.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.databytes.kyc.domain.Applicant;
import com.databytes.kyc.domain.ApplicantStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test (no Quarkus container) of the in-memory store: registering
 * assigns an id, defaults the status to PENDING, and the applicant is then
 * retrievable; an unknown id is a clear miss.
 */
class ApplicantServiceTest {

    private final ApplicantService service = new ApplicantService();

    @Test
    void registerAssignsIdAndPendingStatusThenFindsIt() {
        Applicant saved = service.register(draft("Ada Lovelace", "LY-1001"));

        assertThat(saved.id).isNotNull();
        assertThat(saved.status).isEqualTo(ApplicantStatus.PENDING);
        assertThat(saved.createdAt).isNotNull();

        Applicant found = service.findById(saved.id);
        assertThat(found.nationalId).isEqualTo("LY-1001");
    }

    @Test
    void findByIdOnUnknownApplicantThrows() {
        assertThatThrownBy(() -> service.findById(999_999L))
                .isInstanceOf(ApplicantNotFoundException.class);
    }

    @Test
    void listReturnsRegisteredApplicantsWithinThePage() {
        Applicant first = service.register(draft("First Person", "LY-1"));
        Applicant second = service.register(draft("Second Person", "LY-2"));

        assertThat(service.list(0, 20))
                .extracting(a -> a.id)
                .containsExactlyInAnyOrder(first.id, second.id);
        assertThat(service.list(0, 1)).hasSize(1);
    }

    private static Applicant draft(String fullName, String nationalId) {
        Applicant draft = new Applicant();
        draft.fullName = fullName;
        draft.nationalId = nationalId;
        draft.dateOfBirth = LocalDate.of(1990, 5, 20);
        draft.country = "GB";
        draft.email = "applicant@example.com";
        return draft;
    }
}
```

### `src/test/java/.../api/ApplicantResourceTest.java`

```java
package com.databytes.kyc.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

/** Endpoint tests for the applicant API: happy path, missing applicant, validation. */
@QuarkusTest
class ApplicantResourceTest {

    @Test
    void registerThenFetchApplicant() {
        Integer id = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        { "fullName": "Ada Lovelace", "nationalId": "LY-1001",
                          "dateOfBirth": "1990-05-20", "country": "GB", "email": "ada@example.com" }
                        """)
                .when().post("/applicants")
                .then().statusCode(201)
                .header("Location", containsString("/applicants/"))
                .body("id", notNullValue())
                .body("status", is("PENDING"))
                .body("fullName", is("Ada Lovelace"))
                .extract().path("id");

        given().when().get("/applicants/{id}", id)
                .then().statusCode(200)
                .body("nationalId", is("LY-1001"))
                .body("country", is("GB"));
    }

    @Test
    void unknownApplicantReturns404() {
        given().when().get("/applicants/{id}", 999_999)
                .then().statusCode(404)
                .body("error", is("Not Found"));
    }

    @Test
    void invalidApplicantReturns400WithFieldViolations() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        { "fullName": "", "nationalId": "LY-2", "dateOfBirth": "2999-01-01",
                          "country": "", "email": "not-an-email" }
                        """)
                .when().post("/applicants")
                .then().statusCode(400)
                .body("error", is("Bad Request"))
                .body("violations.field", hasItems("fullName", "country", "dateOfBirth", "email"));
    }
}
```

## Then move on

Day 9 starts from exactly this state and adds the database **underneath** the service:
[`../day-09/COPY-ALONG.md`](../day-09/COPY-ALONG.md).
