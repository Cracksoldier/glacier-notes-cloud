package com.glaciernotes.cloud.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class MigrationUpgradeTest {
    @Inject
    DataSource dataSource;

    @Test
    void upgradesLegacyVersionHashesWithoutDiscardingHistory() throws Exception {
        String schema = "batch4_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target("10")
                .load()
                .migrate();

            UUID owner = UUID.randomUUID();
            UUID notebook = UUID.randomUUID();
            UUID note = UUID.randomUUID();
            UUID version = UUID.randomUUID();
            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.executeUpdate("""
                    insert into %s.app_users(
                      id,username,username_normalized,email,email_normalized,role,status
                    ) values (
                      '%s','legacy','legacy','legacy@example.test','legacy@example.test','USER','ACTIVE'
                    )
                    """.formatted(schema, owner));
                statement.executeUpdate("""
                    insert into %s.notebooks(owner_id,id,name,is_default,sort_order)
                    values ('%s','%s','Notes',true,0)
                    """.formatted(schema, owner, notebook));
                statement.executeUpdate("""
                    insert into %s.notes(owner_id,id,notebook_id,note_type,title,content)
                    values ('%s','%s','%s','text','Legacy','Body')
                    """.formatted(schema, owner, note, notebook));
                statement.executeUpdate("""
                    insert into %s.note_versions(
                      owner_id,id,note_id,source_version,snapshot_reason,content_payload,content_hash
                    ) values (
                      '%s','%s','%s',0,'EDITOR_CLOSE','{"title":"Legacy"}'::jsonb,null
                    )
                    """.formatted(schema, owner, version, note));
            }

            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                     "select length(content_hash) from " + schema + ".note_versions where id='" + version + "'"
                 )) {
                rows.next();
                assertEquals(64, rows.getInt(1));
            }
        } finally {
            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }
}
