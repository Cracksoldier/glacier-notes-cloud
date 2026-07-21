package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
class LifecycleResourceTest {
    private static final String PASSWORD = "correct-horse-battery-staple-2026";
    private static final UUID ADMIN_ID = UUID.fromString("2513dd2c-eb94-41fd-a1f3-7ef6dbad2594");
    private static final UUID USER_ID = UUID.fromString("118c93f3-8eac-437e-bac6-0b07301f622c");

    @Inject DataSource dataSource;
    @Inject PasswordVerifier passwords;

    @BeforeEach
    void prepare() throws SQLException {
        reset();
        insertUser(ADMIN_ID, "admin", "admin@example.com", "ADMIN", "ACTIVE");
    }

    @AfterEach
    void reset() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from endpoint_rate_limits");
            statement.executeUpdate("delete from login_rate_limits");
            statement.executeUpdate("delete from security_tokens");
            statement.executeUpdate("delete from invitations");
            statement.executeUpdate("delete from user_settings");
            statement.executeUpdate("delete from notebooks");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from app_users");
            statement.executeUpdate("""
                update instance_settings set allowed_email_domains = array[]::text[],
                    invitation_expiration_hours = 168, password_reset_expiration_minutes = 60
                where singleton_key = 1
                """);
        }
    }

    @Test
    void invitationIsHashedSingleUseAndCreatesCompleteAccount() throws SQLException {
        var admin = login("admin", PASSWORD);
        var created = adminRequest(admin)
            .body("""
                {"email":"Member@Example.com","proposedUsername":"member","displayName":"Member","role":"USER"}
                """)
            .post("/api/v1/admin/invitations");
        created.then().statusCode(201)
            .body("delivery", equalTo("MANUAL"))
            .body("invitation.email", equalTo("Member@Example.com"))
            .body("token", matchesPattern("^[A-Za-z0-9_-]{43}$"));
        var token = created.jsonPath().getString("token");

        try (var connection = dataSource.getConnection();
             var rows = connection.createStatement().executeQuery("select token_hash from invitations")) {
            rows.next();
            assertNotEquals(token, rows.getString(1));
            assertEquals(64, rows.getString(1).length());
        }

        given().contentType(ContentType.JSON).body("{\"token\":\"" + token + "\"}")
            .post("/api/v1/auth/invitations/inspect").then().statusCode(200)
            .body("emailHint", equalTo("M***@Example.com"));

        given().contentType(ContentType.JSON).body("""
                {"token":"%s","username":"Member.User","displayName":"Member","password":"%s"}
                """.formatted(token, PASSWORD))
            .post("/api/v1/auth/invitations/accept").then().statusCode(204);

        given().contentType(ContentType.JSON).body("""
                {"token":"%s","username":"another","password":"%s"}
                """.formatted(token, PASSWORD))
            .post("/api/v1/auth/invitations/accept").then().statusCode(404)
            .body("errorCode", equalTo("TOKEN_INVALID_OR_EXPIRED"));

        login("member.user", PASSWORD).then().statusCode(200);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            assertEquals(1, count(statement, "select count(*) from app_users where email_normalized='member@example.com'"));
            assertEquals(1, count(statement, "select count(*) from notebooks where is_default and owner_id=(select id from app_users where username_normalized='member.user')"));
            assertEquals(1, count(statement, "select count(*) from user_settings where user_id=(select id from app_users where username_normalized='member.user')"));
        }
    }

    @Test
    void settingsEnforceDomainsAndLastAdministratorRule() {
        var admin = login("admin", PASSWORD);
        adminRequest(admin).body("""
            {"allowedEmailDomains":["EXAMPLE.ORG"],"invitationExpirationHours":48,"passwordResetExpirationMinutes":30}
            """).patch("/api/v1/admin/settings").then().statusCode(200)
            .body("allowedEmailDomains[0]", equalTo("example.org"));

        adminRequest(admin).body("""
            {"email":"member@example.com","role":"USER"}
            """).post("/api/v1/admin/invitations").then().statusCode(422);

        adminRequest(admin).post("/api/v1/admin/users/" + ADMIN_ID + "/deactivate")
            .then().statusCode(409).body("errorCode", equalTo("LAST_ADMIN_REQUIRED"));

        adminRequest(admin).body("{\"role\":\"USER\"}")
            .patch("/api/v1/admin/users/" + ADMIN_ID).then().statusCode(409)
            .body("errorCode", equalTo("LAST_ADMIN_REQUIRED"));
    }

    @Test
    void administrativeResetRevokesSessionsAndDirectoryContainsOnlyMetadata() throws SQLException {
        insertUser(USER_ID, "member", "member@example.com", "USER", "ACTIVE");
        var userLogin = login("member", PASSWORD);
        var admin = login("admin", PASSWORD);

        given().cookie("GLACIER_SESSION", userLogin.getCookie("GLACIER_SESSION"))
            .get("/api/v1/admin/users").then().statusCode(403);

        adminRequest(admin).get("/api/v1/admin/users").then().statusCode(200)
            .body("items", hasSize(2))
            .body("items[0].noteCount", equalTo(0));

        adminRequest(admin).post("/api/v1/admin/users/" + USER_ID + "/deactivate")
            .then().statusCode(204);
        given().cookie("GLACIER_SESSION", userLogin.getCookie("GLACIER_SESSION"))
            .get("/api/v1/auth/session").then().statusCode(401);
        login("member", PASSWORD).then().statusCode(401);
        adminRequest(admin).post("/api/v1/admin/users/" + USER_ID + "/activate")
            .then().statusCode(204);
        userLogin = login("member", PASSWORD);
        userLogin.then().statusCode(200);

        var reset = adminRequest(admin)
            .post("/api/v1/admin/users/" + USER_ID + "/password-reset");
        reset.then().statusCode(201).body("token", matchesPattern("^[A-Za-z0-9_-]{43}$"));
        var token = reset.jsonPath().getString("token");
        var newPassword = "new-correct-horse-battery-2026";
        given().contentType(ContentType.JSON)
            .body("{\"token\":\"" + token + "\",\"password\":\"" + newPassword + "\"}")
            .post("/api/v1/auth/password-reset/complete").then().statusCode(204);

        given().cookie("GLACIER_SESSION", userLogin.getCookie("GLACIER_SESSION"))
            .get("/api/v1/auth/session").then().statusCode(401);
        login("member", PASSWORD).then().statusCode(401);
        login("member", newPassword).then().statusCode(200);
    }

    @Test
    void passwordResetRequestsAreNeutralAndSeparatelyThrottled() {
        for (int attempt = 0; attempt < 5; attempt++) {
            given().contentType(ContentType.JSON)
                .body("{\"email\":\"absent@example.com\"}")
                .post("/api/v1/auth/password-reset/request")
                .then().statusCode(202);
        }

        given().contentType(ContentType.JSON)
            .body("{\"email\":\"absent@example.com\"}")
            .post("/api/v1/auth/password-reset/request")
            .then().statusCode(429)
            .header("Retry-After", matchesPattern("^[1-9][0-9]*$"))
            .body("errorCode", equalTo("LIFECYCLE_RATE_LIMITED"));
    }

    private io.restassured.specification.RequestSpecification adminRequest(Response login) {
        return given().contentType(ContentType.JSON)
            .cookie("GLACIER_SESSION", login.getCookie("GLACIER_SESSION"))
            .cookie("GLACIER_CSRF", login.getCookie("GLACIER_CSRF"))
            .header("X-CSRF-Token", login.getCookie("GLACIER_CSRF"));
    }

    private Response login(String identifier, String password) {
        return given().contentType(ContentType.JSON)
            .body("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\",\"rememberMe\":false}")
            .post("/api/v1/auth/login");
    }

    private void insertUser(UUID id, String username, String email, String role, String status) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into app_users(id,username,username_normalized,email,email_normalized,role,status,
                    password_hash,password_changed_at,activated_at)
                 values(?,?,?,?,?,?,?, ?,current_timestamp,current_timestamp)
                 """)) {
            statement.setObject(1, id);
            statement.setString(2, username);
            statement.setString(3, username.toLowerCase());
            statement.setString(4, email);
            statement.setString(5, email.toLowerCase());
            statement.setString(6, role);
            statement.setString(7, status);
            statement.setString(8, passwords.hash(PASSWORD.toCharArray()));
            statement.executeUpdate();
        }
    }

    private long count(java.sql.Statement statement, String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
