package com.glaciernotes.cloud;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class SpaResourceTest {
    /**
     * Every top-level path in {@code frontend/src/app/app.routes.ts}. A route missing from
     * {@code SpaResource} still works when the client router navigates to it, so only a direct
     * request catches it — which is what a bookmark, a reload, and a link in an email all are.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "/login",
        "/accept-invitation",
        "/forgot-password",
        "/reset-password",
        "/verify-email-change",
        "/settings",
        "/sessions",
        "/notes",
        "/notes/notebooks/018f8ad4-e75a-7ef0-9f23-9cd2a81c4e87",
        "/admin",
        "/admin/settings",
    })
    void servesTheAngularEntryPointForEveryClientRoute(String route) {
        given()
            .when().get(route)
            .then()
            .statusCode(200)
            .contentType("text/html")
            .header("Cache-Control", containsString("no-cache"))
            .body(containsString("glacier-spa-test-entry"));
    }

    /**
     * A cookie left over from an expired session must not keep the user off the page they need in
     * order to sign in again — proactive authentication would otherwise answer the navigation with
     * problem+json the browser renders as raw text.
     */
    @Test
    void servesTheAngularEntryPointDespiteAStaleSessionCookie() {
        given()
            .cookie("GLACIER_SESSION", "stale-session-value")
            .when().get("/login")
            .then()
            .statusCode(200)
            .contentType("text/html");

        given()
            .cookie("GLACIER_SESSION", "stale-session-value")
            .when().get("/api/v1/me/profile")
            .then()
            .statusCode(401);
    }

    @Test
    void doesNotTurnUnknownOrApiRoutesIntoSpaResponses() {
        given().when().get("/unknown-route").then().statusCode(404);
        given().when().get("/api/v1/unknown-route").then().statusCode(404);
    }
}
