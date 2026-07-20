package com.glaciernotes.cloud;

import com.glaciernotes.cloud.api.CorrelationId;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class PingResourceTest {
    @Test
    void pingUsesVersionedContractAndReturnsIncomingCorrelationId() {
        given()
            .header(CorrelationId.HEADER, "test-request-123")
        .when()
            .get("/api/v1/ping")
        .then()
            .statusCode(200)
            .header(CorrelationId.HEADER, equalTo("test-request-123"))
            .body("service", equalTo("glacier-notes-cloud"))
            .body("status", equalTo("ok"))
            .body("apiVersion", equalTo("v1"))
            .body("serverTime", notNullValue());
    }

    @Test
    void invalidCorrelationIdIsReplaced() {
        given()
            .header(CorrelationId.HEADER, "invalid correlation value")
        .when()
            .get("/api/v1/ping")
        .then()
            .statusCode(200)
            .header(
                CorrelationId.HEADER,
                matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            );
    }

    @Test
    void errorsUseProblemJsonAndTheRequestCorrelationId() {
        given()
            .header(CorrelationId.HEADER, "missing-route-test")
        .when()
            .get("/api/v1/does-not-exist")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .header(CorrelationId.HEADER, equalTo("missing-route-test"))
            .body("status", equalTo(404))
            .body("errorCode", equalTo("ENTITY_NOT_FOUND"))
            .body("correlationId", equalTo("missing-route-test"));
    }
}
