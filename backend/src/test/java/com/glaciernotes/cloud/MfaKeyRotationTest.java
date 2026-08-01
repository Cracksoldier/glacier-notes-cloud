package com.glaciernotes.cloud;

import com.glaciernotes.cloud.persistence.repository.MfaRepository;
import com.glaciernotes.cloud.security.EnrollmentSecretCipher;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Swapping the enrollment encryption secret re-derives the key and its fingerprint, but re-encrypts
 * nothing. Enrollments written under the old key are unreadable, and the only supported remedy is to
 * re-enroll — so the instance has to be able to say how many are stranded.
 */
@QuarkusTest
class MfaKeyRotationTest extends SecondFactorTestSupport {
    @Inject
    MfaRepository mfaRepository;

    @Inject
    EnrollmentSecretCipher cipher;

    private Login login;
    private byte[] secret;

    @BeforeEach
    void enrollAnAccount() throws SQLException {
        reset();
        insertUser("rotation.user", "rotation.user@example.com", "USER");
        login = login("rotation.user");
        secret = enroll(login);
    }

    @AfterEach
    void reset() throws SQLException {
        cleanDatabase();
    }

    @Test
    void countsOnlyTheEnrollmentsLeftBehindByASecretSwap() throws SQLException {
        assertEquals(0, mfaRepository.countEnrollmentsUnderOtherKeys(cipher.keyId()));

        // What a rotated secret looks like from the database's side: same rows, foreign fingerprint.
        execute("update user_mfa_totp set key_id = 'deadbeefdeadbeef'");

        assertEquals(1, mfaRepository.countEnrollmentsUnderOtherKeys(cipher.keyId()));
        assertEquals(0, mfaRepository.countEnrollmentsUnderOtherKeys("deadbeefdeadbeef"));
    }

    @Test
    void refusesToCompleteALoginAgainstAnEnrollmentUnderAnotherKey() throws SQLException {
        execute("delete from user_sessions");
        forgetAcceptedStep();
        execute("update user_mfa_totp set key_id = 'deadbeefdeadbeef'");

        var challenge = passwordStep("rotation.user");
        challenge.then().statusCode(200).body("result", equalTo("MFA_REQUIRED"));
        complete(challenge.jsonPath().getString("challenge.token"), currentCode(secret))
            .then().statusCode(not(equalTo(200)));
        assertEquals(0, count("user_sessions", "revoked_at is null"));
    }

    private Response complete(String challengeToken, String code) {
        return given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"challengeToken":"%s","code":"%s"}
                """.formatted(challengeToken, code))
            .when().post("/api/v1/auth/login/mfa");
    }
}
