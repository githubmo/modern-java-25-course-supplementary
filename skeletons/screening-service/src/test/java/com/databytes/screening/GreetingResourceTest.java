package com.databytes.screening;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Proves the skeleton boots and serves a request. Keeps the module green from day one so a
 * failing test always means YOUR change broke something — not a broken starting point.
 */
@QuarkusTest
class GreetingResourceTest {

    @Test
    void helloEndpointResponds() {
        given()
            .when().get("/hello")
            .then()
            .statusCode(200)
            .body(containsString("Screening Service is up"));
    }
}
