package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TransferResourceTest {
    private static final String PASSWORD = "portable-transfer-passphrase-2026";
    private static final UUID ALICE = UUID.fromString("98f5364a-ed31-4d8f-b955-0fb8b6c5920e");
    private static final UUID BOB = UUID.fromString("f10f69e7-108c-45fb-aef2-78d018b033f3");
    private static final UUID ADMIN = UUID.fromString("585161f1-f165-4c50-9dbb-8170a0e27c65");
    private static final UUID ALICE_DEFAULT = UUID.fromString("f8a35a0d-a6c8-4fe5-9a3a-e38500cc5e2e");
    private static final UUID BOB_DEFAULT = UUID.fromString("95ca6d18-8985-43cd-a79d-50435fa1b72d");
    private static final UUID ADMIN_DEFAULT = UUID.fromString("490f2911-29f0-47fc-b14d-bcd073f16ef7");
    private static final String FIXTURE_NOTE = "33333333-3333-4333-8333-333333333333";

    @Inject DataSource dataSource;
    @Inject PasswordVerifier passwordVerifier;

    @BeforeEach
    void setUp() throws Exception {
        reset();
        insertUser(ALICE, "transfer-alice", "USER", ALICE_DEFAULT);
        insertUser(BOB, "transfer-bob", "USER", BOB_DEFAULT);
        insertUser(ADMIN, "transfer-admin", "ADMIN", ADMIN_DEFAULT);
    }

    @AfterEach
    void reset() throws Exception {
        try (var connection = dataSource.getConnection()) {
            try (var rows = connection.createStatement().executeQuery(
                "select temporary_path from transfer_jobs where temporary_path is not null")) {
                while (rows.next()) Files.deleteIfExists(Path.of(rows.getString(1)));
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("delete from external_storage_operations");
                statement.executeUpdate("delete from transfer_jobs");
                statement.executeUpdate("delete from user_sessions");
                statement.executeUpdate("delete from notes");
                statement.executeUpdate("delete from image_assets");
                statement.executeUpdate("delete from audit_events");
                statement.executeUpdate("delete from app_users");
                statement.executeUpdate("update instance_settings set user_exports_enabled=true where singleton_key=1");
            }
        }
    }

    @Test
    void importsDesktopFixtureAndStreamsOwnerScopedExport() throws Exception {
        Session alice = login("transfer-alice");
        Session bob = login("transfer-bob");
        String importId = write(alice).multiPart("file", fixture("full.glacier.json"), "application/json")
            .post("/api/v1/imports").then().statusCode(202).extract().jsonPath().getString("id");

        poll(alice, "/api/v1/imports/" + importId, "READY")
            .then().body("counts.notebooks", equalTo(2)).body("counts.notes", equalTo(2))
            .body("counts.images", equalTo(1)).body("hasConflicts", equalTo(false));
        read(bob).get("/api/v1/imports/{id}", importId).then().statusCode(404);

        writeJson(alice).body("{\"strategy\":\"PRESERVE\"}")
            .post("/api/v1/imports/{id}/apply", importId).then().statusCode(202);
        poll(alice, "/api/v1/imports/" + importId, "SUCCEEDED");
        read(alice).get("/api/v1/notes/{id}", FIXTURE_NOTE).then().statusCode(200)
            .body("title", equalTo("Portable image")).body("imageIds[0]", equalTo("77777777-7777-4777-8777-777777777777"));

        assertExportSucceeds(alice, "NOTEBOOK", "22222222-2222-4222-8222-222222222222");
        assertExportSucceeds(alice, "NOTE", FIXTURE_NOTE);

        String exportId = writeJson(alice).body("{\"scope\":\"ALL\"}")
            .post("/api/v1/exports").then().statusCode(202).extract().jsonPath().getString("id");
        poll(alice, "/api/v1/exports/" + exportId, "SUCCEEDED")
            .then().body("downloadUrl", notNullValue());
        read(bob).get("/api/v1/exports/{id}", exportId).then().statusCode(404);
        String portable = read(alice).get("/api/v1/exports/{id}/download", exportId)
            .then().statusCode(200).header("Cache-Control", equalTo("private, no-store"))
            .extract().asString();
        assertTrue(portable.contains("\"format\":\"glacier-notes-export\""));
        assertTrue(portable.contains(FIXTURE_NOTE));
        assertTrue(portable.contains("\"base64\":"));
        assertFalse(portable.contains("transfer-alice"));
        assertFalse(portable.contains("password"));
        assertFalse(portable.contains("session"));
        assertFalse(portable.contains("audit"));

        String copyId = upload(alice, "full.glacier.json");
        poll(alice, "/api/v1/imports/" + copyId, "READY").then().body("hasConflicts", equalTo(true));
        writeJson(alice).body("{\"strategy\":\"ADD_AS_COPIES\"}")
            .post("/api/v1/imports/{id}/apply", copyId).then().statusCode(202);
        poll(alice, "/api/v1/imports/" + copyId, "SUCCEEDED");
        assertTrue(count("select count(*) from notes where owner_id=?", ALICE) == 4);
        assertTrue(count("select count(*) from image_assets where owner_id=?", ALICE) == 2);

        String replaceId = upload(alice, "full.glacier.json");
        poll(alice, "/api/v1/imports/" + replaceId, "READY").then().body("hasConflicts", equalTo(true));
        writeJson(alice).body("{\"strategy\":\"REPLACE_BY_ID\"}")
            .post("/api/v1/imports/{id}/apply", replaceId).then().statusCode(202);
        poll(alice, "/api/v1/imports/" + replaceId, "SUCCEEDED");
        assertTrue(count("select count(*) from notes where owner_id=?", ALICE) == 4);

        execute("update instance_settings set user_exports_enabled=false where singleton_key=1");
        writeJson(alice).body("{\"scope\":\"ALL\"}").post("/api/v1/exports").then().statusCode(403);
    }

    @Test
    void enforcesBlindAdministrativeImportBoundaryAndAuditsApply() throws Exception {
        Session alice = login("transfer-alice");
        Session admin = login("transfer-admin");
        write(alice).multiPart("file", fixture("notebook.glacier.json"), "application/json")
            .post("/api/v1/admin/users/{userId}/imports", ALICE).then().statusCode(403);

        String id = write(admin).multiPart("file", fixture("notebook.glacier.json"), "application/json")
            .post("/api/v1/admin/users/{userId}/imports", ALICE).then().statusCode(202)
            .extract().jsonPath().getString("id");
        String inspection = poll(admin, "/api/v1/admin/imports/" + id, "READY").asString();
        assertTrue(inspection.contains("\"notes\":1"));
        assertFalse(inspection.contains("Notebook fixture note"));

        writeJson(admin).body("{\"strategy\":\"PRESERVE\"}")
            .post("/api/v1/admin/imports/{id}/apply", id).then().statusCode(202);
        poll(admin, "/api/v1/admin/imports/" + id, "SUCCEEDED");
        assertTrue(count("select count(*) from notes where owner_id=?", ALICE) == 1);
        assertTrue(count("select count(*) from audit_events where actor_user_id=? and event_type='ADMIN_IMPORT_APPLIED'", ADMIN) == 1);
    }

    private Response poll(Session session, String endpoint, String expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        Response response;
        do {
            response = read(session).get(endpoint);
            response.then().statusCode(200);
            String state = response.jsonPath().getString("state");
            if (expected.equals(state)) return response;
            if ("FAILED".equals(state) || "CANCELED".equals(state) || "EXPIRED".equals(state)) {
                throw new AssertionError("Transfer entered " + state + ": " + response.asString());
            }
            Thread.sleep(100);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Transfer did not reach " + expected + ": " + response.asString());
    }

    private File fixture(String name) {
        return Path.of("..", "compatibility-fixtures", "desktop-schema-v1", name).toFile();
    }

    private String upload(Session session, String name) {
        return write(session).multiPart("file", fixture(name), "application/json")
            .post("/api/v1/imports").then().statusCode(202).extract().jsonPath().getString("id");
    }

    private void assertExportSucceeds(Session session, String scope, String resourceId)
        throws InterruptedException {
        String id = writeJson(session).body("""
            {"scope":"%s","resourceId":"%s"}
            """.formatted(scope, resourceId)).post("/api/v1/exports").then().statusCode(202)
            .extract().jsonPath().getString("id");
        poll(session, "/api/v1/exports/" + id, "SUCCEEDED");
        read(session).get("/api/v1/exports/{id}/download", id).then().statusCode(200);
    }

    private Session login(String username) {
        Response response = given().contentType(ContentType.JSON).body("""
            {"identifier":"%s","password":"%s","rememberMe":false}
            """.formatted(username, PASSWORD)).post("/api/v1/auth/login");
        response.then().statusCode(200);
        return new Session(response.getCookie("GLACIER_SESSION"), response.getCookie("GLACIER_CSRF"));
    }

    private RequestSpecification read(Session session) {
        return given().cookie("GLACIER_SESSION", session.session());
    }

    private RequestSpecification write(Session session) {
        return read(session).cookie("GLACIER_CSRF", session.csrf()).header("X-CSRF-Token", session.csrf());
    }

    private RequestSpecification writeJson(Session session) {
        return write(session).contentType(ContentType.JSON);
    }

    private void insertUser(UUID id, String username, String role, UUID notebook) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("""
                insert into app_users(id,username,username_normalized,email,email_normalized,role,status,
                  password_hash,password_changed_at,activated_at)
                values (?,?,?,?,?,?, 'ACTIVE',?,current_timestamp,current_timestamp)
                """)) {
                statement.setObject(1, id); statement.setString(2, username); statement.setString(3, username);
                statement.setString(4, username + "@example.test"); statement.setString(5, username + "@example.test");
                statement.setString(6, role); statement.setString(7, passwordVerifier.hash(PASSWORD.toCharArray()));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                "insert into notebooks(owner_id,id,name,is_default,sort_order) values (?,?,'Notes',true,0)")) {
                statement.setObject(1, id); statement.setObject(2, notebook); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                "insert into user_settings(user_id,last_selected_notebook_id) values (?,?)")) {
                statement.setObject(1, id); statement.setObject(2, notebook); statement.executeUpdate();
            }
        }
    }

    private long count(String sql, UUID id) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private record Session(String session, String csrf) {}
}
