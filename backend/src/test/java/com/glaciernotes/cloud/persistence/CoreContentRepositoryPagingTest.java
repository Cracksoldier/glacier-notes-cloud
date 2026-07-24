package com.glaciernotes.cloud.persistence;

import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.CollectionState;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.NoteQuery;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.TrashState;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class CoreContentRepositoryPagingTest {
    @Inject
    CoreContentRepository repository;

    @Inject
    EntityManagerFactory entityManagerFactory;

    @Inject
    DataSource dataSource;

    @Test
    void listAndSearchBatchLoadSelectedNotesWithoutChangingOrder() throws Exception {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        UUID notebook = UUID.randomUUID();
        List<UUID> ids = new ArrayList<>();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean statisticsWereEnabled = statistics.isStatisticsEnabled();
        try {
            seed(owner, notebook, ids);
            statistics.setStatisticsEnabled(true);
            NoteQuery query = new NoteQuery(
                null, null, null, null, CollectionState.ALL, TrashState.ACTIVE
            );

            statistics.clear();
            var notes = repository.notes(owner, query, null, 10);
            assertEquals(ids.reversed(), notes.stream().map(note -> note.id()).toList());
            assertEquals(2, statistics.getPrepareStatementCount());

            statistics.clear();
            var hits = repository.searchNotes(owner, "batchable", query, null, 10);
            assertEquals(ids.reversed(), hits.stream().map(hit -> hit.note().id()).toList());
            assertEquals(2, statistics.getPrepareStatementCount());
        } finally {
            statistics.setStatisticsEnabled(statisticsWereEnabled);
            try (var connection = dataSource.getConnection()) {
                try (var statement = connection.prepareStatement("delete from notes where owner_id=?")) {
                    statement.setObject(1, owner.value());
                    statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement("delete from app_users where id=?")) {
                    statement.setObject(1, owner.value());
                    statement.executeUpdate();
                }
            }
        }
    }

    private void seed(OwnerId owner, UUID notebook, List<UUID> ids) throws Exception {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("""
                insert into app_users(id,username,username_normalized,email,email_normalized,role,status)
                values (?,?,?,?,?,'USER','ACTIVE')
                """)) {
                String value = owner.value().toString();
                statement.setObject(1, owner.value());
                statement.setString(2, value);
                statement.setString(3, value);
                statement.setString(4, value + "@example.test");
                statement.setString(5, value + "@example.test");
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                insert into notebooks(owner_id,id,name,is_default,sort_order)
                values (?,?,'Notes',true,0)
                """)) {
                statement.setObject(1, owner.value());
                statement.setObject(2, notebook);
                statement.executeUpdate();
            }
            for (int index = 0; index < 5; index++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                try (var statement = connection.prepareStatement("""
                    insert into notes(
                      owner_id,id,notebook_id,note_type,title,content,created_at,updated_at
                    ) values (?,?,?,'text',?,'batchable',?,?)
                    """)) {
                    Instant timestamp = Instant.parse("2026-07-24T12:00:00Z").plusSeconds(index);
                    statement.setObject(1, owner.value());
                    statement.setObject(2, id);
                    statement.setObject(3, notebook);
                    statement.setString(4, "Note " + index);
                    statement.setTimestamp(5, java.sql.Timestamp.from(timestamp));
                    statement.setTimestamp(6, java.sql.Timestamp.from(timestamp));
                    statement.executeUpdate();
                }
            }
        }
    }
}
