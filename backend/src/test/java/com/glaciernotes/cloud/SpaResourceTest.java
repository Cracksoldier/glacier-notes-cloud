package com.glaciernotes.cloud;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SpaResourceTest {
    @Test
    void servesTheAngularEntryPointForClientRoutes() {
        given()
            .when().get("/login")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .header("Cache-Control", containsString("no-cache"))
            .body(containsString("glacier-spa-test-entry"));

        given()
            .when().get("/notes/notebooks/018f8ad4-e75a-7ef0-9f23-9cd2a81c4e87")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("glacier-spa-test-entry"));
    }

    @Test
    void doesNotTurnUnknownOrApiRoutesIntoSpaResponses() {
        given().when().get("/unknown-route").then().statusCode(404);
        given().when().get("/api/v1/unknown-route").then().statusCode(404);
    }
}
