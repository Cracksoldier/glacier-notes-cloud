package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(MfaDisabledTest.Profile.class)
class MfaDisabledTest {
    private static final String PASSWORD = "correct-horse-battery-staple-2026";
    private static final UUID USER_ID = UUID.fromString("6d1c4f7e-1a2b-4c3d-8e9f-0a1b2c3d4e5f");

    @Inject
    DataSource dataSource;

    @Inject
    PasswordVerifier passwordVerifier;

    private String sessionCookie;
    private String csrfToken;

    @BeforeEach
    void createUserAndLogIn() throws SQLException {
        reset();
        insertUser();
        var login = given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"identifier":"flagless.user","password":"%s","rememberMe":false}
                """.formatted(PASSWORD))
            .when().post("/api/v1/auth/login");
        login.then().statusCode(200).body("result", equalTo("SESSION"));
        sessionCookie = login.getCookie("GLACIER_SESSION");
        csrfToken = login.getCookie("GLACIER_CSRF");
    }

    @AfterEach
    void reset() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from login_rate_limits");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from app_users");
        }
    }

    @Test
    void reportsTheFeatureAsUnavailableSoTheClientOffersNoEnrollment() {
        given().cookie("GLACIER_SESSION", sessionCookie)
            .when().get("/api/v1/me/mfa")
            .then().statusCode(200)
            .body("status", equalTo("NONE"))
            .body("available", equalTo(false));
    }

    @Test
    void refusesToStartAnEnrollmentWhileTheFeatureIsOff() {
        given()
            .cookie("GLACIER_SESSION", sessionCookie)
            .cookie("GLACIER_CSRF", csrfToken)
            .header("X-CSRF-Token", csrfToken)
            .contentType(ContentType.JSON)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp")
            .then().statusCode(503).body("errorCode", equalTo("MFA_UNAVAILABLE"));
    }

    private void insertUser() throws SQLException {
        var passwordHash = passwordVerifier.hash(PASSWORD.toCharArray());
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into app_users(
                     id, username, username_normalized, email, email_normalized,
                     display_name, role, status, password_hash, password_changed_at, activated_at
                 ) values (?, ?, ?, ?, ?, ?, 'USER', 'ACTIVE', ?, current_timestamp, current_timestamp)
                 """)) {
            statement.setObject(1, USER_ID);
            statement.setString(2, "flagless.user");
            statement.setString(3, "flagless.user".toLowerCase(Locale.ROOT));
            statement.setString(4, "flagless@example.com");
            statement.setString(5, "flagless@example.com");
            statement.setString(6, "Flagless User");
            statement.setString(7, passwordHash);
            statement.executeUpdate();
        }
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("glacier.mfa.enabled", "false");
        }
    }
}
