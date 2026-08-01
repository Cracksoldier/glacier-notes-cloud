package com.glaciernotes.cloud;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static io.restassured.RestAssured.given;

/**
 * Turning the factor on or off, and replacing the recovery codes, all end every other session: a
 * session opened before the change has never proved possession of the factor.
 */
@QuarkusTest
class MfaSessionRevocationTest extends SecondFactorTestSupport {
    private Login acting;
    private Login bystander;
    private byte[] secret;

    @BeforeEach
    void openTwoSessions() throws SQLException {
        reset();
        insertUser("revoke.user", "revoke.user@example.com", "USER");
        bystander = login("revoke.user");
        acting = login("revoke.user");
        secret = enroll(acting);
    }

    @AfterEach
    void reset() throws SQLException {
        cleanDatabase();
    }

    @Test
    void enablingTheFactorRevokesEveryOtherSession() {
        assertUsable(acting);
        assertRejected(bystander);
    }

    @Test
    void disablingTheFactorRevokesEveryOtherSession() throws SQLException {
        var survivor = anotherSecondFactorSession();

        authenticated(survivor)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp/disable")
            .then().statusCode(204);

        assertUsable(survivor);
        assertRejected(acting);
    }

    @Test
    void regeneratingRecoveryCodesRevokesEveryOtherSession() throws SQLException {
        var survivor = anotherSecondFactorSession();

        authenticated(survivor)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/recovery-codes")
            .then().statusCode(200);

        assertUsable(survivor);
        assertRejected(acting);
    }

    /** Opening a session is not itself a change, so it leaves the enrolling session alive. */
    private Login anotherSecondFactorSession() throws SQLException {
        forgetAcceptedStep();
        var session = secondFactorLogin("revoke.user", currentCode(secret));
        assertUsable(acting);
        return session;
    }

    private void assertUsable(Login login) {
        given().cookie("GLACIER_SESSION", login.session())
            .when().get("/api/v1/auth/session").then().statusCode(200);
    }

    private void assertRejected(Login login) {
        given().cookie("GLACIER_SESSION", login.session())
            .when().get("/api/v1/auth/session").then().statusCode(401);
    }
}
