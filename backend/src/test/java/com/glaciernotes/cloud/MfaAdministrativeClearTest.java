package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.operations.CleanupService;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ordinary remedy for a lost authenticator. Its whole point is to spare an operator the
 * break-glass path, so it must be at least as safe: gated, audited on both outcomes, and silent
 * about everything except whether a factor exists.
 */
@QuarkusTest
class MfaAdministrativeClearTest extends SecondFactorTestSupport {
    @Inject
    CleanupService cleanup;

    @Inject
    MeterRegistry registry;

    private UUID adminId;
    private UUID targetId;
    private Login admin;

    @BeforeEach
    void createAnAdministratorAndATarget() throws SQLException {
        reset();
        adminId = insertUser("admin.user", "admin.user@example.com", "ADMIN");
        targetId = insertUser("member", "member@example.com", "USER");
        admin = login("admin.user");
    }

    @AfterEach
    void reset() throws SQLException {
        cleanDatabase();
    }

    @Test
    void clearingAnEnrolledAccountEndsItsSessionsAndDisclosesNothingBeyondTheState()
        throws SQLException {
        enroll(login("member"));

        clear(null).then().statusCode(200)
            .body("secondFactorActive", equalTo(false))
            .body("secondFactorConfirmedAt", nullValue())
            .body("recoveryCodesRemaining", nullValue());

        assertEquals(0, count("user_mfa_totp", "user_id = '" + targetId + "'"));
        assertEquals(0, count("user_mfa_recovery_codes", "user_id = '" + targetId + "'"));
        assertEquals(0, count("mfa_challenges", "user_id = '" + targetId + "'"));
        assertEquals(0, count("user_sessions", "user_id = '" + targetId + "' and revoked_at is null"));
        assertEquals(1, count("audit_events",
            "event_type = 'MFA_ADMINISTRATIVE_CLEAR' and result = 'SUCCESS'"
                + " and actor_user_id = '" + adminId + "' and target_user_id = '" + targetId + "'"));
        assertEquals("{}", scalar(
            "select metadata_json::text from audit_events where event_type = 'MFA_ADMINISTRATIVE_CLEAR'"
        ).toString());
    }

    @Test
    void anAccountWithoutAFactorIsRejectedRatherThanSilentlyAccepted() throws SQLException {
        clear(null).then().statusCode(409).body("errorCode", equalTo("MFA_NOT_ENROLLED"));

        assertEquals(0, count("audit_events", "event_type = 'MFA_ADMINISTRATIVE_CLEAR'"));
    }

    @Test
    void anEnrolledAdministratorMustProvePossessionAndItsRefusalIsAudited() throws SQLException {
        enroll(login("member"));
        var secret = enroll(admin);
        closeGraceWindow();
        forgetAcceptedStep();

        clear(null).then().statusCode(401).body("errorCode", equalTo("AUTH_MFA_STEP_UP_REQUIRED"));
        assertEquals(1, count("audit_events",
            "event_type = 'MFA_ADMINISTRATIVE_CLEAR' and result = 'DENIED'"
                + " and actor_user_id = '" + adminId + "' and target_user_id = '" + targetId + "'"));
        assertEquals(1, count("user_mfa_totp", "user_id = '" + targetId + "'"));

        clear(currentCode(secret)).then().statusCode(200).body("secondFactorActive", equalTo(false));
    }

    @Test
    void aPlainUserIsRefusedOutright() throws SQLException {
        enroll(login("member"));
        insertUser("plain.user", "plain.user@example.com", "USER");
        var plain = login("plain.user");

        authenticated(plain)
            .body("{\"currentPassword\":\"%s\"}".formatted(PASSWORD))
            .when().post("/api/v1/admin/users/" + targetId + "/second-factor-reset")
            .then().statusCode(403);

        assertEquals(1, count("user_mfa_totp", "user_id = '" + targetId + "'"));
    }

    @Test
    void theTunablesRoundTripAndAnOutOfBoundsValueChangesNothing() {
        authenticated(admin).body("""
            {"mfaChallengeLifetimeMinutes":10,"mfaChallengeAttemptLimit":3,
             "mfaPendingEnrollmentMinutes":15,"mfaStepUpGraceMinutes":0}
            """).patch("/api/v1/admin/settings").then().statusCode(200)
            .body("mfaChallengeLifetimeMinutes", equalTo(10))
            .body("mfaChallengeAttemptLimit", equalTo(3))
            .body("mfaPendingEnrollmentMinutes", equalTo(15))
            .body("mfaStepUpGraceMinutes", equalTo(0));

        authenticated(admin).body("""
            {"mfaChallengeAttemptLimit":99,"mfaStepUpGraceMinutes":30}
            """).patch("/api/v1/admin/settings").then().statusCode(400);

        authenticated(admin).get("/api/v1/admin/settings").then().statusCode(200)
            .body("mfaChallengeAttemptLimit", equalTo(3))
            .body("mfaStepUpGraceMinutes", equalTo(0));
    }

    /** An abandoned challenge is the attack signal; a consumed one is an ordinary sign-in. */
    @Test
    void expiredAndConsumedChallengesAreCountedApart() throws SQLException {
        insertChallenge("expired-hash", "current_timestamp - interval '1 minute'", null);
        insertChallenge("consumed-hash", "current_timestamp + interval '1 hour'",
            "current_timestamp");
        double before = expiredChallenges();

        cleanup.removeMfaChallenges();

        assertEquals(0, count("mfa_challenges", "user_id = '" + targetId + "'"));
        assertEquals(1.0, expiredChallenges() - before);
    }

    private double expiredChallenges() {
        var counter = registry.find("glacier_mfa_challenges").tag("outcome", "expired").counter();
        return counter == null ? 0 : counter.count();
    }

    private void insertChallenge(String tokenHash, String expiresAt, String consumedAt)
        throws SQLException {
        execute("""
            insert into mfa_challenges(id, user_id, token_hash, created_at, expires_at, consumed_at)
            values ('%s', '%s', '%s', current_timestamp - interval '2 hours', %s, %s)
            """.formatted(UUID.randomUUID(), targetId, tokenHash, expiresAt,
            consumedAt == null ? "null" : consumedAt));
    }

    private Response clear(String code) {
        var body = code == null
            ? "{\"currentPassword\":\"%s\"}".formatted(PASSWORD)
            : "{\"currentPassword\":\"%s\",\"code\":\"%s\"}".formatted(PASSWORD, code);
        return authenticated(admin).body(body)
            .when().post("/api/v1/admin/users/" + targetId + "/second-factor-reset");
    }
}
