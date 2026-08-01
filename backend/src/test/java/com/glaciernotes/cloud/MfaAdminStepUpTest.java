package com.glaciernotes.cloud;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Only the two destructive administrative operations are gated: they are the ones that hand an
 * attacker another account outright. Everything else keeps role checks alone.
 */
@QuarkusTest
class MfaAdminStepUpTest extends SecondFactorTestSupport {
    private UUID targetId;
    private Login admin;

    @BeforeEach
    void createAnAdministratorAndATarget() throws SQLException {
        reset();
        insertUser("admin.user", "admin.user@example.com", "ADMIN");
        targetId = insertUser("member", "member@example.com", "USER");
        admin = login("admin.user");
    }

    @AfterEach
    void reset() throws SQLException {
        cleanDatabase();
    }

    @Test
    void anEnrolledAdministratorMustSupplyACodeForDeletionAndForTheResetLink() throws SQLException {
        var secret = enroll(admin);
        closeGraceWindow();
        forgetAcceptedStep();

        scheduleDeletion(null).then()
            .statusCode(401).body("errorCode", equalTo("AUTH_MFA_STEP_UP_REQUIRED"));
        resetLink(null).then()
            .statusCode(401).body("errorCode", equalTo("AUTH_MFA_STEP_UP_REQUIRED"));
        assertEquals(1, count("app_users", "id = '" + targetId + "' and status = 'ACTIVE'"));

        resetLink(currentCode(secret)).then().statusCode(201);
        // The reset link opened the window, so the deletion needs no second code.
        scheduleDeletion(null).then().statusCode(202);
    }

    @Test
    void anEnrolledAdministratorIsToldWhichCredentialIsMissing() throws SQLException {
        enroll(admin);
        closeGraceWindow();

        // The committed client still sends the old bodies, and must fail recognizably rather than
        // as a wrong password.
        adminPost("/deletion", "{\"mode\":\"RETAINED\"}").then()
            .statusCode(401).body("errorCode", equalTo("AUTH_STEP_UP_PASSWORD_REQUIRED"));
        adminPost("/password-reset", "{}").then()
            .statusCode(401).body("errorCode", equalTo("AUTH_STEP_UP_PASSWORD_REQUIRED"));
    }

    @Test
    void anAdministratorWithoutAnEnrollmentSuppliesThePasswordAlone() {
        resetLink(null).then().statusCode(201);
        scheduleDeletion(null).then().statusCode(202);
    }

    @Test
    void theUngatedAdministrativeOperationsAreUnchanged() {
        adminPost("/deactivate", null).then().statusCode(204);
        adminPost("/activate", null).then().statusCode(204);
        authenticated(admin).when().delete("/api/v1/admin/users/" + targetId + "/sessions")
            .then().statusCode(204);
    }

    @Test
    void aPlainUserIsStillRefusedOutrightWhateverItSupplies() throws SQLException {
        insertUser("plain.user", "plain.user@example.com", "USER");
        var plain = login("plain.user");

        authenticated(plain)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/admin/users/" + targetId + "/password-reset")
            .then().statusCode(403);
    }

    private Response scheduleDeletion(String code) {
        return adminPost("/deletion", body("\"mode\":\"RETAINED\",", code));
    }

    private Response resetLink(String code) {
        return adminPost("/password-reset", body("", code));
    }

    private String body(String prefix, String code) {
        return code == null
            ? "{%s\"currentPassword\":\"%s\"}".formatted(prefix, PASSWORD)
            : "{%s\"currentPassword\":\"%s\",\"code\":\"%s\"}".formatted(prefix, PASSWORD, code);
    }

    private Response adminPost(String suffix, String body) {
        var request = authenticated(admin);
        if (body != null) {
            request = request.body(body);
        }
        return request.when().post("/api/v1/admin/users/" + targetId + suffix);
    }
}
