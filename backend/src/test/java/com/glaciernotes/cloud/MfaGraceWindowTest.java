package com.glaciernotes.cloud;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The window is what keeps an enrolled user from typing a code twice in one minute. It is scoped to
 * the session that proved possession and is never inferred from anything the client sends.
 */
@QuarkusTest
class MfaGraceWindowTest extends SecondFactorTestSupport {
    private Login login;
    private byte[] secret;

    @BeforeEach
    void enrollAnAccount() throws SQLException {
        reset();
        insertUser("grace.user", "grace.user@example.com", "USER");
        login = login("grace.user");
        secret = enroll(login);
    }

    @AfterEach
    void reset() throws SQLException {
        cleanDatabase();
    }

    @Test
    void confirmingAnEnrollmentOpensTheWindowSoTheNextOperationIsNotPromptedAgain() throws SQLException {
        assertNotNull(verifiedAt());
        regenerate(null).then().statusCode(200);
    }

    @Test
    void aSecondFactorLoginOpensTheWindowAndAPasswordOnlyLoginDoesNot() throws SQLException {
        execute("delete from user_sessions");
        forgetAcceptedStep();

        var second = secondFactorLogin("grace.user", currentCode(secret));
        assertNotNull(verifiedAt());
        login = second;
        regenerate(null).then().statusCode(200);

        // A password-only login is only reachable once the factor is gone.
        execute("delete from user_mfa_totp");
        execute("delete from user_sessions");
        login = login("grace.user");
        assertNull(verifiedAt());
    }

    @Test
    void anExpiredWindowPromptsAgain() throws SQLException {
        execute("update user_sessions set second_factor_verified_at = "
            + "second_factor_verified_at - interval '6 minutes'");

        regenerate(null).then().statusCode(401).body("errorCode", equalTo("AUTH_MFA_STEP_UP_REQUIRED"));
    }

    @Test
    void aWindowOfZeroPromptsEveryTime() throws SQLException {
        execute("update instance_settings set mfa_step_up_grace_minutes = 0");

        regenerate(null).then().statusCode(401).body("errorCode", equalTo("AUTH_MFA_STEP_UP_REQUIRED"));
        forgetAcceptedStep();
        regenerate(currentCode(secret)).then().statusCode(200);
        // Even a successful step-up leaves the next operation prompted.
        regenerate(null).then().statusCode(401).body("errorCode", equalTo("AUTH_MFA_STEP_UP_REQUIRED"));
    }

    @Test
    void theWindowNeverTransfersToAnotherSession() throws SQLException {
        closeGraceWindow();
        forgetAcceptedStep();
        var other = secondFactorLogin("grace.user", currentCode(secret));

        // Only the session that supplied the code has a window.
        assertEquals(1, count("user_sessions",
            "second_factor_verified_at is not null and revoked_at is null"));

        // The enrolling session is prompted even though another session just proved possession.
        regenerate(null).then().statusCode(401).body("errorCode", equalTo("AUTH_MFA_STEP_UP_REQUIRED"));

        authenticated(other)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/recovery-codes")
            .then().statusCode(200);
    }

    @Test
    void disablingTheFactorClearsEveryWindow() throws SQLException {
        authenticated(login)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp/disable")
            .then().statusCode(204);

        assertEquals(0, count("user_sessions", "second_factor_verified_at is not null"));
    }

    /**
     * The window rides on the session row, and a password change revokes every session — so proving
     * possession before the change can never carry over to whoever holds the new password.
     */
    @Test
    void changingThePasswordLeavesNoWindowBehind() throws SQLException {
        assertNotNull(verifiedAt());

        authenticated(login)
            .body("""
                {"currentPassword":"%s","newPassword":"replacement-horse-battery-staple-2026"}
                """.formatted(PASSWORD))
            .when().put("/api/v1/me/password")
            .then().statusCode(204);

        assertEquals(0, count("user_sessions",
            "second_factor_verified_at is not null and revoked_at is null"));
        regenerate(null).then().statusCode(401);
    }

    @Test
    void reEnrollingClearsEveryWindowBeforeOpeningItsOwn() throws SQLException {
        authenticated(login)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp/disable")
            .then().statusCode(204);

        var replacement = enroll(login);
        assertEquals(1, count("user_sessions", "second_factor_verified_at is not null"));
        assertFalse(java.util.Arrays.equals(secret, replacement));
    }

    private io.restassured.response.Response regenerate(String code) {
        var body = code == null
            ? """
              {"currentPassword":"%s"}
              """.formatted(PASSWORD)
            : """
              {"currentPassword":"%s","code":"%s"}
              """.formatted(PASSWORD, code);
        return authenticated(login).body(body).when().post("/api/v1/me/mfa/recovery-codes");
    }

    private Object verifiedAt() throws SQLException {
        return scalar("select max(second_factor_verified_at) from user_sessions");
    }
}
