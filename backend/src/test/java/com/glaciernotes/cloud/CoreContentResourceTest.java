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
import java.sql.SQLException;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CoreContentResourceTest {
    private static final String PASSWORD = "correct-horse-battery-staple-2026";
    private static final UUID ALICE_ID = UUID.fromString("80d6da90-2784-44a1-bf7d-05d9e68c065c");
    private static final UUID BOB_ID = UUID.fromString("36e46558-82db-420f-b866-247beee96da0");
    private static final UUID ALICE_DEFAULT = UUID.fromString("512a32a3-5fea-4411-8065-2ae90a6507ca");
    private static final UUID BOB_DEFAULT = UUID.fromString("3dd87887-2537-415a-ac0c-d82f5e6f4659");

    @Inject
    DataSource dataSource;

    @Inject
    PasswordVerifier passwordVerifier;

    @BeforeEach
    void setUp() throws SQLException {
        reset();
        insertUser(ALICE_ID, "alice", "alice@example.test", "USER", ALICE_DEFAULT);
        insertUser(BOB_ID, "bob", "bob@example.test", "ADMIN", BOB_DEFAULT);
    }

    @AfterEach
    void reset() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from endpoint_rate_limits");
            statement.executeUpdate("delete from login_rate_limits");
            statement.executeUpdate("delete from security_tokens");
            statement.executeUpdate("delete from invitations");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from notes");
            statement.executeUpdate("delete from tombstones");
            statement.executeUpdate("delete from app_users");
        }
    }

    @Test
    void managesNotebooksTransactionallyAndKeepsEveryOperationOwnerScoped() {
        Session alice = login("alice");
        Session bob = login("bob");

        read(alice).get("/api/v1/notebooks/default").then()
            .statusCode(200).body("id", equalTo(ALICE_DEFAULT.toString()))
            .body("defaultNotebook", equalTo(true)).body("noteCount", equalTo(0));

        Response created = write(alice).body("{\"name\":\"Projects\",\"color\":\"BLUE\"}")
            .post("/api/v1/notebooks");
        created.then().statusCode(201).body("name", equalTo("Projects"))
            .body("color", equalTo("BLUE")).body("version", equalTo(0));
        String notebookId = created.jsonPath().getString("id");

        read(bob).get("/api/v1/notebooks/{id}", notebookId).then()
            .statusCode(404).body("errorCode", equalTo("ENTITY_NOT_FOUND"));
        write(bob).body("{\"name\":\"Stolen\",\"version\":0}")
            .patch("/api/v1/notebooks/{id}", notebookId).then().statusCode(404);

        write(alice).body("{\"name\":\"Work\",\"color\":\"GREEN\",\"version\":0}")
            .patch("/api/v1/notebooks/{id}", notebookId).then()
            .statusCode(200).body("version", equalTo(1)).body("name", equalTo("Work"));
        write(alice).body("{\"name\":\"Old\",\"version\":0}")
            .patch("/api/v1/notebooks/{id}", notebookId).then()
            .statusCode(409).body("errorCode", equalTo("CONTENT_VERSION_CONFLICT"))
            .body("currentVersion", equalTo(1));

        write(alice).body("""
            {"notebooks":[
              {"id":"%s","version":1},
              {"id":"%s","version":0}
            ]}
            """.formatted(notebookId, ALICE_DEFAULT)).post("/api/v1/notebooks/reorder").then()
            .statusCode(200).body("[0].id", equalTo(notebookId)).body("[0].sortOrder", equalTo(0));

        write(alice).delete("/api/v1/notebooks/{id}?strategy=MOVE_TO_DEFAULT&version=1", ALICE_DEFAULT)
            .then().statusCode(409).body("errorCode", equalTo("CONTENT_CONFLICT"));

        Response note = write(alice).body("""
            {"notebookId":"%s","noteType":"TEXT","title":"Movable","content":"Body"}
            """.formatted(notebookId)).post("/api/v1/notes");
        note.then().statusCode(201);
        String noteId = note.jsonPath().getString("id");
        long reorderedVersion = read(alice).get("/api/v1/notebooks/{id}", notebookId)
            .jsonPath().getLong("version");
        write(alice).delete(
            "/api/v1/notebooks/{id}?strategy=MOVE_TO_DEFAULT&version={version}",
            notebookId, reorderedVersion
        ).then().statusCode(204);
        read(alice).get("/api/v1/notes/{id}", noteId).then()
            .statusCode(200).body("notebookId", equalTo(ALICE_DEFAULT.toString()));

        Response destination = write(alice).body("{\"name\":\"Temporary\"}")
            .post("/api/v1/notebooks");
        String destinationId = destination.jsonPath().getString("id");
        write(alice).body("""
            {"notebookId":"%s","version":1}
            """.formatted(destinationId)).post("/api/v1/notes/{id}/move", noteId).then()
            .statusCode(200).body("notebookId", equalTo(destinationId)).body("version", equalTo(2));
        write(alice).delete(
            "/api/v1/notebooks/{id}?strategy=TRASH_NOTES&version=0", destinationId
        ).then().statusCode(204);
        read(alice).get("/api/v1/notes/{id}", noteId).then()
            .statusCode(200).body("notebookId", equalTo(ALICE_DEFAULT.toString()))
            .body("deletedAt", notNullValue()).body("version", equalTo(3));
    }

    @Test
    void supportsLabelsChecklistReplacementFilteringPaginationAndAdminIsolation() throws SQLException {
        Session alice = login("alice");
        Session bob = login("bob");
        Response aliceLabel = write(alice).body("{\"name\":\"Important\"}").post("/api/v1/labels");
        Response bobLabel = write(bob).body("{\"name\":\"IMPORTANT\"}").post("/api/v1/labels");
        aliceLabel.then().statusCode(201);
        bobLabel.then().statusCode(201);
        String labelId = aliceLabel.jsonPath().getString("id");
        String bobLabelId = bobLabel.jsonPath().getString("id");
        assertNotEquals(labelId, bobLabelId);
        write(alice).body("{\"name\":\"important\"}").post("/api/v1/labels").then()
            .statusCode(409).body("errorCode", equalTo("CONTENT_CONFLICT"));

        write(alice).body("""
            {"noteType":"TEXT","title":"Bad","labelIds":["%s"]}
            """.formatted(bobLabelId)).post("/api/v1/notes").then().statusCode(404);

        UUID portableId = UUID.randomUUID();
        Response note = write(alice).body("""
            {
              "id":"%s","noteType":"CHECKLIST","title":"Groceries","content":"",
              "checklistItems":[
                {"text":"Milk","checked":false},
                {"text":"Bread","checked":true}
              ],
              "pinned":true,"archived":false,"color":"YELLOW","labelIds":["%s"]
            }
            """.formatted(portableId, labelId)).post("/api/v1/notes");
        note.then().statusCode(201).body("id", equalTo(portableId.toString()))
            .body("checklistItems", hasSize(2)).body("version", equalTo(0));
        String retainedItem = note.jsonPath().getString("checklistItems[1].id");
        String removedItem = note.jsonPath().getString("checklistItems[0].id");

        write(bob).body("""
            {"id":"%s","noteType":"TEXT","title":"Bob's copy"}
            """.formatted(portableId)).post("/api/v1/notes").then().statusCode(201);
        read(bob).get("/api/v1/notes/{id}", portableId).then()
            .statusCode(200).body("title", equalTo("Bob's copy"));
        write(bob).body("{\"version\":0}").post("/api/v1/notes/{id}/trash", portableId)
            .then().statusCode(200).body("title", equalTo("Bob's copy"));
        read(alice).get("/api/v1/notes/{id}", portableId).then()
            .statusCode(200).body("title", equalTo("Groceries"));

        write(alice).body("""
            {
              "title":"Shopping","content":"","checklistItems":[
                {"id":"%s","text":"Wholegrain bread","checked":false},
                {"text":"Apples","checked":false}
              ],
              "pinned":true,"archived":true,"color":"GREEN","labelIds":["%s"],"version":0
            }
            """.formatted(retainedItem, labelId)).patch("/api/v1/notes/{id}", portableId).then()
            .statusCode(200).body("checklistItems", hasSize(2)).body("version", equalTo(1))
            .body("archived", equalTo(true));
        assertEquals(1, count("select count(*) from tombstones where owner_id = ? and entity_id = ?",
            ALICE_ID, UUID.fromString(removedItem)));

        write(alice).body("{\"noteType\":\"TEXT\",\"title\":\"Active\",\"content\":\"preview\"}")
            .post("/api/v1/notes").then().statusCode(201);
        Response pageOne = read(alice).queryParam("archive", "ALL").queryParam("limit", 1)
            .get("/api/v1/notes");
        pageOne.then().statusCode(200).body("items", hasSize(1)).body("page.hasNext", equalTo(true))
            .body("page.nextCursor", notNullValue());
        String firstId = pageOne.jsonPath().getString("items[0].id");
        Response pageTwo = read(alice).queryParam("archive", "ALL").queryParam("limit", 1)
            .queryParam("cursor", pageOne.jsonPath().getString("page.nextCursor")).get("/api/v1/notes");
        pageTwo.then().statusCode(200).body("items", hasSize(1));
        assertNotEquals(firstId, pageTwo.jsonPath().getString("items[0].id"));
        read(alice).queryParam("noteType", "CHECKLIST").queryParam("archive", "ARCHIVED")
            .queryParam("pinned", true).get("/api/v1/notes").then()
            .statusCode(200).body("items", hasSize(1)).body("items[0].id", equalTo(portableId.toString()));

        write(alice).body("{\"name\":\"Urgent\",\"version\":0}")
            .patch("/api/v1/labels/{id}", labelId).then()
            .statusCode(200).body("name", equalTo("Urgent")).body("version", equalTo(1));
        write(alice).delete("/api/v1/labels/{id}?version=1", labelId).then().statusCode(204);
        read(alice).get("/api/v1/notes/{id}", portableId).then()
            .statusCode(200).body("labelIds", hasSize(0));
    }

    @Test
    void convertsNotesAndImplementsRestorePurgeEmptyTrashAndCsrf() throws SQLException {
        Session alice = login("alice");
        Response created = write(alice).body("""
            {"noteType":"TEXT","title":"Convert","content":"- [x] done\\nplain line\\n* [ ] later"}
            """).post("/api/v1/notes");
        created.then().statusCode(201);
        String noteId = created.jsonPath().getString("id");

        write(alice).body("{\"targetType\":\"CHECKLIST\",\"version\":0}")
            .post("/api/v1/notes/{id}/convert", noteId).then()
            .statusCode(200).body("noteType", equalTo("CHECKLIST"))
            .body("checklistItems", hasSize(3)).body("checklistItems[0].checked", equalTo(true))
            .body("checklistItems[1].text", equalTo("plain line")).body("version", equalTo(1));
        write(alice).body("{\"targetType\":\"TEXT\",\"version\":1}")
            .post("/api/v1/notes/{id}/convert", noteId).then()
            .statusCode(200).body("noteType", equalTo("TEXT"))
            .body("content", equalTo("- [x] done\n- [ ] plain line\n- [ ] later"))
            .body("checklistItems", hasSize(0)).body("version", equalTo(2));

        given().cookie("GLACIER_SESSION", alice.session()).contentType(ContentType.JSON)
            .body("{\"version\":2}").post("/api/v1/notes/{id}/trash", noteId).then()
            .statusCode(403).body("errorCode", equalTo("CSRF_INVALID"));
        write(alice).body("{\"version\":2}").post("/api/v1/notes/{id}/trash", noteId).then()
            .statusCode(200).body("deletedAt", notNullValue()).body("version", equalTo(3));
        read(alice).get("/api/v1/notes").then().statusCode(200).body("items", hasSize(0));
        read(alice).queryParam("trash", "TRASHED").queryParam("archive", "ALL")
            .get("/api/v1/notes").then().statusCode(200).body("items", hasSize(1));
        write(alice).body("{\"version\":3}").post("/api/v1/notes/{id}/restore", noteId).then()
            .statusCode(200).body("deletedAt", nullValue()).body("version", equalTo(4));
        write(alice).body("{\"version\":4}").post("/api/v1/notes/{id}/trash", noteId).then()
            .statusCode(200).body("version", equalTo(5));
        write(alice).delete("/api/v1/notes/{id}?version=5", noteId).then().statusCode(204);
        read(alice).get("/api/v1/notes/{id}", noteId).then().statusCode(404);
        assertEquals(1, count("select count(*) from tombstones where owner_id = ? and entity_type = 'NOTE' and entity_id = ?",
            ALICE_ID, UUID.fromString(noteId)));
        assertTrue(count("select count(*) from tombstones where owner_id = ? and expires_at >= current_timestamp + interval '29 days'",
            ALICE_ID, null) >= 1);

        for (int index = 0; index < 2; index++) {
            Response response = write(alice).body("{\"noteType\":\"TEXT\",\"title\":\"Trash\"}")
                .post("/api/v1/notes");
            write(alice).body("{\"version\":0}").post(
                "/api/v1/notes/{id}/trash", response.jsonPath().getString("id")
            ).then().statusCode(200);
        }
        write(alice).delete("/api/v1/notes/trash").then()
            .statusCode(200).body("purgedCount", equalTo(2));
    }

    @Test
    void returnsTheSameSafeNotFoundForEveryCrossOwnerEntityOperation() {
        Session alice = login("alice");
        Session admin = login("bob");
        Response notebook = write(alice).body("{\"name\":\"Private\"}").post("/api/v1/notebooks");
        Response label = write(alice).body("{\"name\":\"Private\"}").post("/api/v1/labels");
        String notebookId = notebook.jsonPath().getString("id");
        String labelId = label.jsonPath().getString("id");
        Response note = write(alice).body("""
            {
              "notebookId":"%s","noteType":"TEXT","title":"Secret","content":"private",
              "labelIds":["%s"]
            }
            """.formatted(notebookId, labelId)).post("/api/v1/notes");
        String noteId = note.jsonPath().getString("id");

        read(admin).get("/api/v1/notebooks/{id}", notebookId).then().statusCode(404);
        write(admin).body("{\"name\":\"No\",\"version\":0}")
            .patch("/api/v1/notebooks/{id}", notebookId).then().statusCode(404);
        write(admin).delete(
            "/api/v1/notebooks/{id}?strategy=MOVE_TO_DEFAULT&version=0", notebookId
        ).then().statusCode(404);
        read(admin).queryParam("notebookId", notebookId).get("/api/v1/notes").then().statusCode(404);

        write(admin).body("{\"name\":\"No\",\"version\":0}")
            .patch("/api/v1/labels/{id}", labelId).then().statusCode(404);
        write(admin).delete("/api/v1/labels/{id}?version=0", labelId).then().statusCode(404);
        read(admin).queryParam("labelId", labelId).get("/api/v1/notes").then().statusCode(404);

        read(admin).get("/api/v1/notes/{id}", noteId).then()
            .statusCode(404).body("errorCode", equalTo("ENTITY_NOT_FOUND"));
        write(admin).body("""
            {
              "title":"No","content":"","checklistItems":[],"pinned":false,
              "archived":false,"color":"DEFAULT","labelIds":[],"version":0
            }
            """).patch("/api/v1/notes/{id}", noteId).then().statusCode(404);
        write(admin).body("{\"version\":0}").post("/api/v1/notes/{id}/trash", noteId)
            .then().statusCode(404);
        write(admin).body("{\"version\":0}").post("/api/v1/notes/{id}/restore", noteId)
            .then().statusCode(404);
        write(admin).body("""
            {"notebookId":"%s","version":0}
            """.formatted(BOB_DEFAULT)).post("/api/v1/notes/{id}/move", noteId)
            .then().statusCode(404);
        write(admin).body("{\"targetType\":\"CHECKLIST\",\"version\":0}")
            .post("/api/v1/notes/{id}/convert", noteId).then().statusCode(404);
        write(admin).delete("/api/v1/notes/{id}?version=0", noteId).then().statusCode(404);

        read(alice).get("/api/v1/notes/{id}", noteId).then()
            .statusCode(200).body("title", equalTo("Secret"));
    }

    @Test
    void searchesOwnedTextAndChecklistContentWithFiltersRankingAndPagination() {
        Session alice = login("alice");
        Session bob = login("bob");
        Response label = write(alice).body("{\"name\":\"Research\"}").post("/api/v1/labels");
        String labelId = label.jsonPath().getString("id");

        Response titleMatch = write(alice).body("""
            {"noteType":"TEXT","title":"Glacier expedition","content":"field notes",
             "pinned":true,"labelIds":["%s"]}
            """.formatted(labelId)).post("/api/v1/notes");
        String titleId = titleMatch.jsonPath().getString("id");
        Response bodyMatch = write(alice).body("""
            {"noteType":"TEXT","title":"Travel","content":"glacier observations","archived":true}
            """).post("/api/v1/notes");
        String bodyId = bodyMatch.jsonPath().getString("id");
        Response checklist = write(alice).body("""
            {"noteType":"CHECKLIST","title":"Supplies","checklistItems":[
              {"text":"thermal blanket","checked":false}]}
            """).post("/api/v1/notes");
        String checklistId = checklist.jsonPath().getString("id");
        Response trashed = write(alice).body("""
            {"noteType":"TEXT","title":"Glacier discarded","content":"old"}
            """).post("/api/v1/notes");
        String trashedId = trashed.jsonPath().getString("id");
        write(alice).body("{\"version\":0}").post("/api/v1/notes/{id}/trash", trashedId)
            .then().statusCode(200);
        write(bob).body("""
            {"noteType":"TEXT","title":"Glacier secret","content":"private"}
            """).post("/api/v1/notes").then().statusCode(201);

        Response ranked = read(alice).queryParam("query", "glacier").queryParam("limit", 1)
            .get("/api/v1/notes/search");
        ranked.then().statusCode(200).body("items", hasSize(1))
            .body("items[0].id", equalTo(titleId)).body("page.hasNext", equalTo(true));
        Response secondPage = read(alice).queryParam("query", "glacier").queryParam("limit", 1)
            .queryParam("cursor", ranked.jsonPath().getString("page.nextCursor"))
            .get("/api/v1/notes/search");
        secondPage.then().statusCode(200).body("items", hasSize(1))
            .body("items[0].id", equalTo(bodyId)).body("page.hasNext", equalTo(false));

        read(alice).queryParam("query", "thermal").get("/api/v1/notes/search").then()
            .statusCode(200).body("items", hasSize(1)).body("items[0].id", equalTo(checklistId));
        read(alice).queryParam("query", "glacier").queryParam("archive", "ACTIVE")
            .queryParam("pinned", true).queryParam("labelId", labelId)
            .get("/api/v1/notes/search").then().statusCode(200)
            .body("items", hasSize(1)).body("items[0].id", equalTo(titleId));
        read(alice).queryParam("query", "glacier").queryParam("trash", "TRASHED")
            .get("/api/v1/notes/search").then().statusCode(200)
            .body("items", hasSize(1)).body("items[0].id", equalTo(trashedId));
        read(alice).queryParam("query", "   ").get("/api/v1/notes/search").then()
            .statusCode(200).body("items", hasSize(0)).body("page.hasNext", equalTo(false));

        write(alice).body("""
            {"title":"Supplies","content":"","checklistItems":[
              {"id":"%s","text":"satellite beacon","checked":false}],
             "pinned":false,"archived":false,"color":"DEFAULT","labelIds":[],"imageIds":[],"version":0}
            """.formatted(checklist.jsonPath().getString("checklistItems[0].id")))
            .patch("/api/v1/notes/{id}", checklistId).then().statusCode(200);
        read(alice).queryParam("query", "thermal").get("/api/v1/notes/search").then()
            .statusCode(200).body("items", hasSize(0));
        read(alice).queryParam("query", "satellite").get("/api/v1/notes/search").then()
            .statusCode(200).body("items", hasSize(1)).body("items[0].id", equalTo(checklistId));
    }

    @Test
    void recordsDeduplicatesAndRestoresHistoryWhileRejectingTwoSessionConflicts() throws SQLException {
        Session firstSession = login("alice");
        Session secondSession = login("alice");
        Session bob = login("bob");
        Response created = write(firstSession).body("""
            {"noteType":"TEXT","title":"First","content":"one"}
            """).post("/api/v1/notes");
        String noteId = created.jsonPath().getString("id");

        write(firstSession).body("""
            {"title":"Second","content":"two","checklistItems":[],"pinned":false,
             "archived":false,"color":"DEFAULT","labelIds":[],"imageIds":[],"version":0}
            """).patch("/api/v1/notes/{id}", noteId).then().statusCode(200).body("version", equalTo(1));
        write(firstSession).body("{\"version\":1}")
            .post("/api/v1/notes/{id}/versions/snapshot", noteId).then().statusCode(204);
        write(firstSession).body("{\"version\":1}")
            .post("/api/v1/notes/{id}/versions/snapshot", noteId).then().statusCode(204);
        assertEquals(1, count("select count(*) from note_versions where owner_id = ? and note_id = ?",
            ALICE_ID, UUID.fromString(noteId)));

        write(firstSession).body("""
            {"title":"Third","content":"three","checklistItems":[],"pinned":false,
             "archived":false,"color":"DEFAULT","labelIds":[],"imageIds":[],"version":1}
            """).patch("/api/v1/notes/{id}", noteId).then().statusCode(200).body("version", equalTo(2));
        write(secondSession).body("""
            {"title":"Stale overwrite","content":"lost","checklistItems":[],"pinned":false,
             "archived":false,"color":"DEFAULT","labelIds":[],"imageIds":[],"version":1}
            """).patch("/api/v1/notes/{id}", noteId).then().statusCode(409)
            .body("errorCode", equalTo("CONTENT_VERSION_CONFLICT"))
            .body("currentVersion", equalTo(2)).body("currentUpdatedAt", notNullValue());
        read(firstSession).get("/api/v1/notes/{id}", noteId).then().statusCode(200)
            .body("title", equalTo("Third")).body("content", equalTo("three"));

        Response history = read(firstSession).get("/api/v1/notes/{id}/versions", noteId);
        history.then().statusCode(200).body("items", hasSize(2))
            .body("items[0].reason", equalTo("CONFLICT"));
        String olderVersionId = history.jsonPath().getString("items[1].id");
        read(bob).get("/api/v1/notes/{id}/versions", noteId).then().statusCode(404);
        read(firstSession).get("/api/v1/notes/{id}/versions/{versionId}", noteId, olderVersionId)
            .then().statusCode(200).body("title", equalTo("Second"));

        write(firstSession).body("{\"version\":2}")
            .post("/api/v1/notes/{id}/versions/{versionId}/restore", noteId, olderVersionId)
            .then().statusCode(200).body("id", equalTo(noteId)).body("title", equalTo("Second"))
            .body("version", equalTo(3));
        read(firstSession).get("/api/v1/notes/{id}/versions", noteId).then().statusCode(200)
            .body("items", hasSize(2));
    }

    private Session login(String username) {
        Response response = given().contentType(ContentType.JSON)
            .body("""
                {"identifier":"%s","password":"%s","rememberMe":false}
                """.formatted(username, PASSWORD)).post("/api/v1/auth/login");
        response.then().statusCode(200);
        return new Session(response.getCookie("GLACIER_SESSION"), response.getCookie("GLACIER_CSRF"));
    }

    private RequestSpecification read(Session session) {
        return given().cookie("GLACIER_SESSION", session.session());
    }

    private RequestSpecification write(Session session) {
        return given().cookie("GLACIER_SESSION", session.session())
            .cookie("GLACIER_CSRF", session.csrf()).header("X-CSRF-Token", session.csrf())
            .contentType(ContentType.JSON);
    }

    private void insertUser(UUID id, String username, String email, String role, UUID defaultNotebook)
        throws SQLException {
        String passwordHash = passwordVerifier.hash(PASSWORD.toCharArray());
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("""
                insert into app_users(
                    id, username, username_normalized, email, email_normalized, display_name,
                    role, status, password_hash, password_changed_at, activated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, current_timestamp, current_timestamp)
                """)) {
                statement.setObject(1, id);
                statement.setString(2, username);
                statement.setString(3, username);
                statement.setString(4, email);
                statement.setString(5, email);
                statement.setString(6, username);
                statement.setString(7, role);
                statement.setString(8, passwordHash);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                insert into notebooks(owner_id, id, name, is_default, sort_order)
                values (?, ?, 'Notes', true, 0)
                """)) {
                statement.setObject(1, id);
                statement.setObject(2, defaultNotebook);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                "insert into user_settings(user_id, last_selected_notebook_id) values (?, ?)"
            )) {
                statement.setObject(1, id);
                statement.setObject(2, defaultNotebook);
                statement.executeUpdate();
            }
        }
    }

    private long count(String sql, UUID ownerId, UUID entityId) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, ownerId);
            if (entityId != null) statement.setObject(2, entityId);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private record Session(String session, String csrf) {}
}
