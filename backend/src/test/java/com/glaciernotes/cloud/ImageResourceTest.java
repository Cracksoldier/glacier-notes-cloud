package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ImageResourceTest {
    private static final String PASSWORD = "correct-horse-battery-staple-2026";
    private static final UUID ALICE = UUID.fromString("9ef68cf8-f2f8-459e-832d-f5fd590e43ad");
    private static final UUID BOB = UUID.fromString("7909cbd9-7805-4658-aefd-ce470d353b53");
    private static final UUID ALICE_NOTEBOOK = UUID.fromString("1ebff753-5f50-49c4-b65e-588847509227");
    private static final UUID BOB_NOTEBOOK = UUID.fromString("5091dcb7-f94d-4e5f-9a6e-95b68b0a58dd");

    @Inject DataSource dataSource;
    @Inject PasswordVerifier passwordVerifier;
    @Inject BinaryAssetStorage imageStorage;

    @BeforeEach
    void setUp() throws SQLException {
        reset(); insertUser(ALICE, "image-alice", ALICE_NOTEBOOK); insertUser(BOB, "image-bob", BOB_NOTEBOOK);
    }

    @AfterEach
    void reset() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from external_storage_operations");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from notes");
            statement.executeUpdate("delete from image_assets");
            statement.executeUpdate("delete from app_users");
        }
    }

    @Test
    void uploadsNormalizesStreamsScopesReferencesAndDeletesImages() throws Exception {
        Session alice = login("image-alice"); Session bob = login("image-bob");
        Response upload = write(alice).multiPart("file", "camera.png", png(), "image/png")
            .post("/api/v1/images");
        upload.then().statusCode(201).body("mimeType", equalTo("image/jpeg"))
            .body("width", equalTo(900)).body("height", equalTo(600))
            .body("thumbnailWidth", equalTo(480)).body("byteSize", greaterThan(0));
        String id = upload.jsonPath().getString("id");
        String storageKey = storageKey(UUID.fromString(id));

        read(alice).get("/api/v1/images/{id}", id).then().statusCode(200)
            .contentType("image/jpeg").header("Cache-Control", equalTo("private, max-age=86400"));
        read(alice).get("/api/v1/images/{id}/thumbnail", id).then().statusCode(200).contentType("image/jpeg");
        read(bob).get("/api/v1/images/{id}/metadata", id).then().statusCode(404)
            .body("errorCode", equalTo("ENTITY_NOT_FOUND"));
        read(alice).get("/api/v1/me/storage").then().statusCode(200)
            .body("imageCount", equalTo(1)).body("usedBytes", greaterThan(0));

        Response note = writeJson(alice).body("""
            {"noteType":"TEXT","title":"Photo","content":"![camera](glacier-img://%s)","imageIds":["%s"]}
            """.formatted(id, id)).post("/api/v1/notes");
        note.then().statusCode(201).body("imageIds[0]", equalTo(id));
        String noteId = note.jsonPath().getString("id");
        write(alice).delete("/api/v1/images/{id}", id).then().statusCode(409)
            .body("errorCode", equalTo("IMAGE_STILL_REFERENCED"));

        writeJson(alice).body("""
            {"title":"Photo","content":"","checklistItems":[],"pinned":false,"archived":false,
             "color":"DEFAULT","labelIds":[],"imageIds":[],"version":0}
            """).patch("/api/v1/notes/{id}", noteId).then().statusCode(200).body("imageIds.size()", equalTo(0));
        write(alice).delete("/api/v1/images/{id}", id).then().statusCode(204);
        read(alice).get("/api/v1/images/{id}", id).then().statusCode(404);
        awaitPhysicalDeletion(storageKey);

        write(alice).multiPart("file", "fake.png", "not-an-image".getBytes(), "image/png")
            .post("/api/v1/images").then().statusCode(422).body("errorCode", equalTo("IMAGE_TYPE_UNSUPPORTED"));
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(new Color(33, 120, 170)); graphics.fillRect(0, 0, 900, 600); graphics.dispose();
        var output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return output.toByteArray();
    }

    private Session login(String username) {
        Response response = given().contentType(ContentType.JSON).body("""
            {"identifier":"%s","password":"%s","rememberMe":false}
            """.formatted(username, PASSWORD)).post("/api/v1/auth/login");
        response.then().statusCode(200);
        return new Session(response.getCookie("GLACIER_SESSION"), response.getCookie("GLACIER_CSRF"));
    }

    private RequestSpecification read(Session session) { return given().cookie("GLACIER_SESSION", session.session); }
    private RequestSpecification write(Session session) {
        return given().cookie("GLACIER_SESSION", session.session).cookie("GLACIER_CSRF", session.csrf)
            .header("X-CSRF-Token", session.csrf);
    }
    private RequestSpecification writeJson(Session session) { return write(session).contentType(ContentType.JSON); }

    private void insertUser(UUID id, String username, UUID notebook) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("""
                insert into app_users(id,username,username_normalized,email,email_normalized,role,status,password_hash,password_changed_at,activated_at)
                values (?,?,?,?,?,'USER','ACTIVE',?,current_timestamp,current_timestamp)
                """)) {
                statement.setObject(1, id); statement.setString(2, username); statement.setString(3, username);
                statement.setString(4, username + "@example.test"); statement.setString(5, username + "@example.test");
                statement.setString(6, passwordVerifier.hash(PASSWORD.toCharArray())); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("insert into notebooks(owner_id,id,name,is_default,sort_order) values (?,?,'Notes',true,0)")) {
                statement.setObject(1, id); statement.setObject(2, notebook); statement.executeUpdate();
            }
        }
    }

    private String storageKey(UUID id) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select storage_key from image_assets where owner_id=? and id=?")) {
            statement.setObject(1, ALICE);
            statement.setObject(2, id);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private void awaitPhysicalDeletion(String key) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            try (var ignored = imageStorage.load(key).stream()) {
                Thread.sleep(100);
            } catch (RuntimeException failure) {
                return;
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
        assertThrows(RuntimeException.class, () -> imageStorage.load(key));
    }

    private record Session(String session, String csrf) {}
}
