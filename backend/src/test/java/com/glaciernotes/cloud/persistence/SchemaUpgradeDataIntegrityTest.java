package com.glaciernotes.cloud.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Broader sibling of {@link MigrationUpgradeTest}: seeds one row across every content, ownership,
 * and session/audit table that exists at the V10 baseline, migrates to head, and asserts every row
 * and its foreign-key relationships survive intact — not just the single backfilled column that
 * {@link MigrationUpgradeTest} checks.
 */
@QuarkusTest
class SchemaUpgradeDataIntegrityTest {
    @Inject
    DataSource dataSource;

    @Test
    void seededRowsAcrossAllContentTablesSurviveUpgradeToHeadWithReferentialIntegrityIntact() throws Exception {
        String schema = "batch4_integrity_" + UUID.randomUUID().toString().replace("-", "");
        UUID owner = UUID.randomUUID();
        UUID notebook = UUID.randomUUID();
        UUID note = UUID.randomUUID();
        UUID checklistItem = UUID.randomUUID();
        UUID label = UUID.randomUUID();
        UUID image = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        UUID audit = UUID.randomUUID();
        String storageKey = "images/" + UUID.randomUUID() + ".png";

        try {
            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target("10")
                .load()
                .migrate();

            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.executeUpdate("""
                    insert into %1$s.app_users(
                      id,username,username_normalized,email,email_normalized,role,status
                    ) values (
                      '%2$s','integrityuser','integrityuser','integrity@example.test',
                      'integrity@example.test','USER','ACTIVE'
                    )
                    """.formatted(schema, owner));
                statement.executeUpdate("""
                    insert into %1$s.notebooks(owner_id,id,name,is_default,sort_order)
                    values ('%2$s','%3$s','Notes',true,0)
                    """.formatted(schema, owner, notebook));
                statement.executeUpdate("""
                    insert into %1$s.notes(owner_id,id,notebook_id,note_type,title,content)
                    values ('%2$s','%3$s','%4$s','checklist','Integrity Note','Body')
                    """.formatted(schema, owner, note, notebook));
                statement.executeUpdate("""
                    insert into %1$s.checklist_items(owner_id,id,note_id,text,checked,sort_order)
                    values ('%2$s','%3$s','%4$s','Item',false,0)
                    """.formatted(schema, owner, checklistItem, note));
                statement.executeUpdate("""
                    insert into %1$s.labels(owner_id,id,name,name_normalized)
                    values ('%2$s','%3$s','Label','label')
                    """.formatted(schema, owner, label));
                statement.executeUpdate("""
                    insert into %1$s.note_labels(owner_id,note_id,label_id)
                    values ('%2$s','%3$s','%4$s')
                    """.formatted(schema, owner, note, label));
                statement.executeUpdate("""
                    insert into %1$s.image_assets(
                      owner_id,id,mime_type,byte_size,width,height,content_hash,
                      storage_backend,storage_key
                    ) values (
                      '%2$s','%3$s','image/png',4,1,1,'deadbeef','POSTGRESQL','%4$s'
                    )
                    """.formatted(schema, owner, image, storageKey));
                statement.executeUpdate("""
                    insert into %1$s.image_asset_blobs(storage_key,content,content_length,content_type)
                    values ('%2$s',decode('89504e47','hex'),4,'image/png')
                    """.formatted(schema, storageKey));
                statement.executeUpdate("""
                    insert into %1$s.note_image_references(owner_id,note_id,image_id,sort_order)
                    values ('%2$s','%3$s','%4$s',0)
                    """.formatted(schema, owner, note, image));
                statement.executeUpdate("""
                    insert into %1$s.note_versions(
                      owner_id,id,note_id,source_version,snapshot_reason,content_payload,content_hash
                    ) values (
                      '%2$s','%3$s','%4$s',0,'EDITOR_CLOSE','{"title":"Integrity Note"}'::jsonb,null
                    )
                    """.formatted(schema, owner, version, note));
                statement.executeUpdate("""
                    insert into %1$s.note_version_image_references(owner_id,note_version_id,image_id)
                    values ('%2$s','%3$s','%4$s')
                    """.formatted(schema, owner, version, image));
                statement.executeUpdate("""
                    insert into %1$s.user_sessions(id,user_id,token_hash,expires_at)
                    values ('%2$s','%3$s','integrity-session-hash',CURRENT_TIMESTAMP + INTERVAL '1 day')
                    """.formatted(schema, session, owner));
                statement.executeUpdate("""
                    insert into %1$s.audit_events(id,event_type,actor_user_id,result,correlation_id)
                    values ('%2$s','INTEGRITY_TEST','%3$s','SUCCESS','integrity-correlation')
                    """.formatted(schema, audit, owner));
            }

            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                assertEquals(1, count(statement,
                    "select count(*) from %s.app_users where id='%s'".formatted(schema, owner)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.notebooks where owner_id='%s' and id='%s'".formatted(schema, owner, notebook)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.notes where owner_id='%s' and id='%s'".formatted(schema, owner, note)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.checklist_items where owner_id='%s' and id='%s'".formatted(schema, owner, checklistItem)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.labels where owner_id='%s' and id='%s'".formatted(schema, owner, label)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.note_labels where owner_id='%s' and note_id='%s' and label_id='%s'"
                        .formatted(schema, owner, note, label)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.image_assets where owner_id='%s' and id='%s'".formatted(schema, owner, image)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.image_asset_blobs where storage_key='%s'".formatted(schema, storageKey)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.note_image_references where owner_id='%s' and note_id='%s' and image_id='%s'"
                        .formatted(schema, owner, note, image)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.note_versions where owner_id='%s' and id='%s'".formatted(schema, owner, version)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.note_version_image_references where owner_id='%s' and note_version_id='%s' and image_id='%s'"
                        .formatted(schema, owner, version, image)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.user_sessions where id='%s'".formatted(schema, session)));
                assertEquals(1, count(statement,
                    "select count(*) from %s.audit_events where id='%s'".formatted(schema, audit)));

                try (var rows = statement.executeQuery(
                    "select length(content_hash) from " + schema + ".note_versions where id='" + version + "'"
                )) {
                    rows.next();
                    assertEquals(64, rows.getInt(1));
                }

                assertEquals(1, count(statement, """
                    select count(*) from %1$s.notes n
                    join %1$s.notebooks b on n.owner_id = b.owner_id and n.notebook_id = b.id
                    where n.owner_id='%2$s' and n.id='%3$s'
                    """.formatted(schema, owner, note)));
                assertEquals(1, count(statement, """
                    select count(*) from %1$s.checklist_items i
                    join %1$s.notes n on i.owner_id = n.owner_id and i.note_id = n.id
                    where i.owner_id='%2$s' and i.id='%3$s'
                    """.formatted(schema, owner, checklistItem)));
                assertEquals(1, count(statement, """
                    select count(*) from %1$s.note_image_references r
                    join %1$s.image_assets ia on r.owner_id = ia.owner_id and r.image_id = ia.id
                    join %1$s.image_asset_blobs bl on ia.storage_key = bl.storage_key
                    where r.owner_id='%2$s' and r.note_id='%3$s' and r.image_id='%4$s'
                    """.formatted(schema, owner, note, image)));
            }
        } finally {
            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }

    private static int count(java.sql.Statement statement, String sql) throws java.sql.SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
