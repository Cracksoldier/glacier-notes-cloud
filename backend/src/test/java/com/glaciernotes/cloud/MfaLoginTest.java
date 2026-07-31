package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.security.Base32Codec;
import com.glaciernotes.cloud.security.TotpVerifier;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MfaLoginTest {
    private static final String PASSWORD = "correct-horse-battery-staple-2026";
    private static final String USERNAME = "second.factor";
    private static final UUID USER_ID = UUID.fromString("7f3a5d2e-2b41-4a53-9c0e-1f8b6d4e0a22");
    private static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int ATTEMPT_LIMIT = 5;

    @Inject
    DataSource dataSource;

    @Inject
    PasswordVerifier passwordVerifier;

    @Inject
    TotpVerifier totp;

    private byte[] secret;
    private List<String> recoveryCodes;

    @BeforeEach
    void enrollTheAccount() throws SQLException {
        reset();
        insertUser();
        var firstLogin = passwordStep(PASSWORD, false);
        firstLogin.then().statusCode(200).body("result", equalTo("SESSION"));
        var session = firstLogin.getCookie("GLACIER_SESSION");
        var csrf = firstLogin.getCookie("GLACIER_CSRF");

        var start = given()
            .cookie("GLACIER_SESSION", session).cookie("GLACIER_CSRF", csrf)
            .header("X-CSRF-Token", csrf).contentType(ContentType.JSON)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp");
        start.then().statusCode(200);
        secret = Base32Codec.decode(start.jsonPath().getString("secret"));

        recoveryCodes = given()
            .cookie("GLACIER_SESSION", session).cookie("GLACIER_CSRF", csrf)
            .header("X-CSRF-Token", csrf).contentType(ContentType.JSON)
            .body("""
                {"code":"%s"}
                """.formatted(codeAtOffset(0)))
            .when().post("/api/v1/me/mfa/totp/confirm")
            .then().statusCode(200).extract().jsonPath().getList("codes", String.class);

        // The enrolling session and the step consumed by confirmation would otherwise contaminate
        // assertions about the very next login.
        execute("delete from user_sessions");
        execute("delete from login_rate_limits");
        execute("delete from audit_events");
        execute("update user_mfa_totp set last_accepted_step = null");
        execute("update app_users set last_login_at = null, failed_login_count = 0");
    }

    @AfterEach
    void reset() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from endpoint_rate_limits");
            statement.executeUpdate("delete from login_rate_limits");
            statement.executeUpdate("delete from mfa_challenges");
            statement.executeUpdate("delete from user_mfa_recovery_codes");
            statement.executeUpdate("delete from user_mfa_totp");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from app_users");
            // Session lifetimes and the lock threshold are administrator-editable, and other suites
            // leave them changed; this test asserts against the shipped defaults.
            statement.executeUpdate("""
                update instance_settings
                   set normal_session_duration_minutes = 720,
                       remember_session_duration_minutes = 43200,
                       login_delay_threshold = 5,
                       login_lock_threshold = %d,
                       login_lock_minutes = 15,
                       mfa_challenge_lifetime_minutes = 5,
                       mfa_challenge_attempt_limit = %d
                """.formatted(2 * ATTEMPT_LIMIT, ATTEMPT_LIMIT));
        }
    }

    @Test
    void thePasswordStepIssuesNoSessionAndClearsNoLoginState() throws SQLException {
        passwordStep("wrong-password-value", false).then().statusCode(401);
        assertEquals(1, count("login_rate_limits", "scope = 'IDENTIFIER'"));
        assertEquals(1, count("app_users", "failed_login_count = 1"));

        var challenge = passwordStep(PASSWORD, false);
        challenge.then()
            .statusCode(200)
            .body("result", equalTo("MFA_REQUIRED"))
            .body("context", nullValue())
            .body("challenge.token", matchesPattern("^[A-Za-z0-9_-]{43}$"))
            .body("challenge.expiresAt", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T.*Z$"))
            .body("challenge.attemptsRemaining", equalTo(ATTEMPT_LIMIT))
            .body("challenge.acceptedFactors", contains("TOTP", "RECOVERY_CODE"));

        assertTrue(challenge.getHeaders().getValues("Set-Cookie").isEmpty());
        assertEquals(0, count("user_sessions", "true"));
        assertEquals(1, count("app_users", "last_login_at is null and failed_login_count = 1"));
        // A caller holding the password must not be able to reset the identifier limiter by
        // repeating the first step while brute-forcing the second.
        assertEquals(1, count("login_rate_limits", "scope = 'IDENTIFIER'"));

        var token = challenge.jsonPath().getString("challenge.token");
        assertEquals(1, count("mfa_challenges", "consumed_at is null and attempt_count = 0"));
        assertEquals(0, count(
            "mfa_challenges", "token_hash = '" + token.replace("'", "''") + "'"
        ));
    }

    @Test
    void theSecondStepIssuesTheSessionAndSettlesTheLoginState() throws SQLException {
        passwordStep("wrong-password-value", false).then().statusCode(401);
        var token = challengeToken(passwordStep(PASSWORD, false));

        var completed = complete(token, codeAtOffset(0));
        completed.then()
            .statusCode(200)
            .body("user.id", equalTo(USER_ID.toString()))
            .body("session.current", equalTo(true))
            .body("session.rememberMe", equalTo(false));
        assertNotNull(completed.getCookie("GLACIER_SESSION"));
        assertNotNull(completed.getCookie("GLACIER_CSRF"));

        assertEquals(1, count("user_sessions", "revoked_at is null"));
        assertEquals(1, count("app_users", "last_login_at is not null and failed_login_count = 0"));
        assertEquals(0, count("login_rate_limits", "scope = 'IDENTIFIER'"));
        assertEquals(1, count("mfa_challenges", "consumed_at is not null"));
        assertEquals(1, count(
            "audit_events", "event_type = 'MFA_CHALLENGE_COMPLETED' and result = 'SUCCESS'"
        ));

        given().cookie("GLACIER_SESSION", completed.getCookie("GLACIER_SESSION"))
            .when().get("/api/v1/auth/session")
            .then().statusCode(200).body("session.current", equalTo(true));
    }

    @Test
    void acceptsTheNeighbouringStepsRejectsWiderOnesAndNeverRepeatsAStep() throws SQLException {
        for (long offset : new long[]{-1, 0, 1}) {
            var token = challengeToken(passwordStep(PASSWORD, false));
            complete(token, codeAtOffset(offset)).then()
                .statusCode(200)
                .body("session.current", equalTo(true));
            execute("update user_mfa_totp set last_accepted_step = null");
        }

        for (long offset : new long[]{-2, 2}) {
            var token = challengeToken(passwordStep(PASSWORD, false));
            complete(token, codeAtOffset(offset)).then()
                .statusCode(401)
                .body("errorCode", equalTo("AUTH_MFA_INVALID_CODE"));
        }

        var replayed = codeAtOffset(0);
        complete(challengeToken(passwordStep(PASSWORD, false)), replayed).then().statusCode(200);
        complete(challengeToken(passwordStep(PASSWORD, false)), replayed).then()
            .statusCode(401)
            .body("errorCode", equalTo("AUTH_MFA_INVALID_CODE"));
    }

    @Test
    void acceptsEachRecoveryCodeExactlyOnce() throws SQLException {
        var code = recoveryCodes.getFirst();

        complete(challengeToken(passwordStep(PASSWORD, false)), code).then()
            .statusCode(200)
            .body("session.current", equalTo(true));
        assertEquals(1, count("user_mfa_recovery_codes", "used_at is not null"));
        assertEquals(9, count("user_mfa_recovery_codes", "used_at is null"));
        assertEquals(1, count("audit_events", "event_type = 'MFA_RECOVERY_CODE_USED'"));

        complete(challengeToken(passwordStep(PASSWORD, false)), code).then()
            .statusCode(401)
            .body("errorCode", equalTo("AUTH_MFA_INVALID_CODE"));
        // A lower-case, ungrouped rendering of a still-unused code is the same code.
        complete(
            challengeToken(passwordStep(PASSWORD, false)),
            recoveryCodes.get(1).replace("-", "").toLowerCase(Locale.ROOT)
        ).then().statusCode(200);
        assertEquals(8, count("user_mfa_recovery_codes", "used_at is null"));
    }

    @Test
    void carriesRememberMeFromThePasswordStepAndIgnoresAnySecondStepAttemptToSetIt() throws SQLException {
        var remembered = complete(challengeToken(passwordStep(PASSWORD, true)), codeAtOffset(0));
        remembered.then().statusCode(200).body("session.rememberMe", equalTo(true));
        assertEquals(2_592_000L, sessionLifetimeSeconds());

        execute("delete from user_sessions");
        execute("update user_mfa_totp set last_accepted_step = null");

        var token = challengeToken(passwordStep(PASSWORD, false));
        // The second step has no rememberMe field, and the contract refuses one rather than
        // silently ignoring it.
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"challengeToken":"%s","code":"%s","rememberMe":true}
                """.formatted(token, codeAtOffset(0)))
            .when().post("/api/v1/auth/login/mfa")
            .then().statusCode(400);
        assertEquals(0, count("user_sessions", "true"));

        complete(token, codeAtOffset(0)).then()
            .statusCode(200)
            .body("session.rememberMe", equalTo(false));
        assertEquals(43_200L, sessionLifetimeSeconds());
    }

    @Test
    void treatsAnAccountChangedBetweenTheStepsAsAnUnknownChallenge() throws SQLException {
        var unknown = complete("not-a-challenge-token", codeAtOffset(0));
        unknown.then().statusCode(401).body("errorCode", equalTo("AUTH_MFA_CHALLENGE_INVALID"));
        var baseline = withoutCorrelationId(unknown);

        for (var mutation : List.of(
            "update app_users set status = 'DEACTIVATED'",
            "update app_users set locked_until = current_timestamp + interval '10 minutes'",
            "update app_users set password_changed_at = current_timestamp + interval '1 minute'"
        )) {
            execute("update app_users set status = 'ACTIVE', locked_until = null,"
                + " password_changed_at = current_timestamp - interval '1 day'");
            var token = challengeToken(passwordStep(PASSWORD, false));
            execute(mutation);

            var response = complete(token, codeAtOffset(0));
            response.then().statusCode(401);
            assertEquals(baseline, withoutCorrelationId(response), mutation);
            // The challenge is destroyed rather than left for another attempt.
            assertEquals(0, count("mfa_challenges", "consumed_at is null"), mutation);
        }
    }

    @Test
    void refusesAnExpiredOrAlreadyConsumedChallenge() throws SQLException {
        var expired = challengeToken(passwordStep(PASSWORD, false));
        execute("""
            update mfa_challenges
               set created_at = current_timestamp - interval '10 minutes',
                   expires_at = current_timestamp - interval '1 second'
            """);
        complete(expired, codeAtOffset(0)).then()
            .statusCode(401)
            .body("errorCode", equalTo("AUTH_MFA_CHALLENGE_INVALID"));

        execute("delete from mfa_challenges");
        var consumed = challengeToken(passwordStep(PASSWORD, false));
        complete(consumed, codeAtOffset(0)).then().statusCode(200);
        complete(consumed, codeAtOffset(1)).then()
            .statusCode(401)
            .body("errorCode", equalTo("AUTH_MFA_CHALLENGE_INVALID"));
    }

    @Test
    void exhaustsTheChallengeAndEventuallyLocksTheAccount() throws SQLException {
        var token = challengeToken(passwordStep(PASSWORD, false));
        for (int attempt = 1; attempt < ATTEMPT_LIMIT; attempt++) {
            complete(token, "000000").then()
                .statusCode(401)
                .body("errorCode", equalTo("AUTH_MFA_INVALID_CODE"));
            expireCooldowns();
        }
        complete(token, "000000").then()
            .statusCode(429)
            .header("Retry-After", matchesPattern("^[1-9]\\d*$"))
            .body("errorCode", equalTo("AUTH_MFA_ATTEMPTS_EXCEEDED"));

        assertEquals(0, count("mfa_challenges", "consumed_at is null"));
        assertEquals(1, count("app_users", "failed_login_count = " + ATTEMPT_LIMIT));
        expireCooldowns();
        complete(token, codeAtOffset(0)).then()
            .statusCode(401)
            .body("errorCode", equalTo("AUTH_MFA_CHALLENGE_INVALID"));

        expireCooldowns();
        var second = challengeToken(passwordStep(PASSWORD, false));
        for (int attempt = 0; attempt < ATTEMPT_LIMIT; attempt++) {
            complete(second, "000000");
            expireCooldowns();
        }
        assertEquals(1, count("app_users", "status = 'LOCKED' and locked_until is not null"));
        // A locked account does not even reach the second step.
        expireCooldowns();
        passwordStep(PASSWORD, false).then().statusCode(not(equalTo(200)));
        assertEquals(0, count("user_sessions", "true"));
    }

    @Test
    void keepsAtMostThreeOpenChallengesPerAccount() throws SQLException {
        var oldest = challengeToken(passwordStep(PASSWORD, false));
        var tokens = new ArrayList<String>();
        for (int index = 0; index < 3; index++) {
            tokens.add(challengeToken(passwordStep(PASSWORD, false)));
        }

        assertEquals(3, count("mfa_challenges", "consumed_at is null"));
        complete(oldest, codeAtOffset(0)).then()
            .statusCode(401)
            .body("errorCode", equalTo("AUTH_MFA_CHALLENGE_INVALID"));
        complete(tokens.getLast(), codeAtOffset(0)).then().statusCode(200);
    }

    @Test
    void resolvesConcurrentCompletionsToASingleSession() throws Exception {
        var token = challengeToken(passwordStep(PASSWORD, false));
        var code = codeAtOffset(0);

        var statuses = inParallel(() -> complete(token, code).statusCode());

        assertEquals(1, statuses.stream().filter(status -> status == 200).count(), statuses.toString());
        assertEquals(1, count("user_sessions", "true"));

        execute("delete from user_sessions");
        var first = challengeToken(passwordStep(PASSWORD, false));
        var second = challengeToken(passwordStep(PASSWORD, false));
        var recoveryCode = recoveryCodes.getFirst();
        var tokens = List.of(first, second);
        var index = new java.util.concurrent.atomic.AtomicInteger();
        var recoveryStatuses = inParallel(
            () -> complete(tokens.get(index.getAndIncrement()), recoveryCode).statusCode()
        );

        assertEquals(
            1, recoveryStatuses.stream().filter(status -> status == 200).count(),
            recoveryStatuses.toString()
        );
        assertEquals(1, count("user_mfa_recovery_codes", "used_at is not null"));
        assertEquals(1, count("user_sessions", "true"));
    }

    private List<Integer> inParallel(Callable<Integer> work) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(
                executor.submit(() -> {
                    barrier.await();
                    return work.call();
                }),
                executor.submit(() -> {
                    barrier.await();
                    return work.call();
                })
            );
            var statuses = new ArrayList<Integer>();
            for (var future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        }
    }

    private Response passwordStep(String password, boolean rememberMe) {
        return given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"identifier":"%s","password":"%s","rememberMe":%s}
                """.formatted(USERNAME, password, rememberMe))
            .when().post("/api/v1/auth/login");
    }

    private String challengeToken(Response passwordStep) {
        passwordStep.then().statusCode(200).body("result", equalTo("MFA_REQUIRED"));
        return passwordStep.jsonPath().getString("challenge.token");
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

    private String withoutCorrelationId(Response response) {
        var problem = new java.util.TreeMap<>(response.jsonPath().getMap("$"));
        problem.remove("correlationId");
        problem.remove("instance");
        return problem.toString();
    }

    private String codeAtOffset(long stepOffset) {
        awaitStableStep();
        return totp.generate(secret, Instant.now().getEpochSecond() / PERIOD_SECONDS + stepOffset, DIGITS);
    }

    /**
     * Codes are generated against the client clock and verified against the server clock, so a code
     * produced within a second of a step boundary can land in a neighbouring window.
     */
    private void awaitStableStep() {
        long secondsIntoStep = Instant.now().getEpochSecond() % PERIOD_SECONDS;
        if (secondsIntoStep < PERIOD_SECONDS - 2) {
            return;
        }
        try {
            Thread.sleep(2_500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private long sessionLifetimeSeconds() throws SQLException {
        return ((Number) scalar(
            "select extract(epoch from expires_at - created_at) from user_sessions"
        )).longValue();
    }

    private void expireCooldowns() throws SQLException {
        execute("update login_rate_limits set blocked_until = current_timestamp - interval '1 second'");
    }

    private long count(String table, String condition) throws SQLException {
        return ((Number) scalar("select count(*) from " + table + " where " + condition)).longValue();
    }

    private Object scalar(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getObject(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void insertUser() throws SQLException {
        var passwordHash = passwordVerifier.hash(PASSWORD.toCharArray());
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into app_users(
                     id, username, username_normalized, email, email_normalized,
                     display_name, role, status, password_hash, password_changed_at, activated_at
                 ) values (?, ?, ?, ?, ?, ?, 'USER', 'ACTIVE', ?,
                           current_timestamp - interval '1 day', current_timestamp)
                 """)) {
            statement.setObject(1, USER_ID);
            statement.setString(2, USERNAME);
            statement.setString(3, USERNAME);
            statement.setString(4, "second.factor@example.com");
            statement.setString(5, "second.factor@example.com");
            statement.setString(6, "Second Factor");
            statement.setString(7, passwordHash);
            statement.executeUpdate();
        }
        assertNull(scalarOrNull("select last_login_at from app_users"));
    }

    private Object scalarOrNull(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            return rows.next() ? rows.getObject(1) : null;
        }
    }
}
