package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.persistence.entity.ChecklistItemEntity;
import com.glaciernotes.cloud.persistence.entity.LabelEntity;
import com.glaciernotes.cloud.persistence.entity.NoteEntity;
import com.glaciernotes.cloud.persistence.entity.NotebookEntity;
import com.glaciernotes.cloud.persistence.entity.OwnedEntityId;
import com.glaciernotes.cloud.persistence.entity.TombstoneEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CoreContentRepository {
    private final EntityManager entityManager;

    public CoreContentRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<NotebookEntity> notebooks(OwnerId ownerId, boolean lock) {
        var query = entityManager.createQuery(
            "select n from NotebookEntity n where n.key.ownerId = :owner order by n.sortOrder, n.key.id",
            NotebookEntity.class
        ).setParameter("owner", ownerId.value());
        if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList();
    }

    public Optional<NotebookEntity> notebook(OwnerId ownerId, UUID id, boolean lock) {
        NotebookEntity entity = entityManager.find(
            NotebookEntity.class, new OwnedEntityId(ownerId.value(), id),
            lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE
        );
        return Optional.ofNullable(entity);
    }

    public Optional<NotebookEntity> defaultNotebook(OwnerId ownerId, boolean lock) {
        var query = entityManager.createQuery(
            "select n from NotebookEntity n where n.key.ownerId = :owner and n.defaultNotebook = true",
            NotebookEntity.class
        ).setParameter("owner", ownerId.value());
        if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultStream().findFirst();
    }

    public long notebookNoteCount(OwnerId ownerId, UUID notebookId) {
        return entityManager.createQuery(
            "select count(n) from NoteEntity n where n.key.ownerId = :owner " +
                "and n.notebookId = :notebook and n.deletedAt is null", Long.class
        ).setParameter("owner", ownerId.value()).setParameter("notebook", notebookId)
            .getSingleResult();
    }

    public void persistNotebook(OwnerId ownerId, NotebookEntity notebook) {
        requireOwner(ownerId, notebook.key().ownerId());
        entityManager.persist(notebook);
    }

    public void removeNotebook(OwnerId ownerId, NotebookEntity notebook) {
        requireOwner(ownerId, notebook.key().ownerId());
        entityManager.remove(notebook);
    }

    public Optional<NoteEntity> note(OwnerId ownerId, UUID id, boolean lock) {
        NoteEntity entity = entityManager.find(
            NoteEntity.class, new OwnedEntityId(ownerId.value(), id),
            lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE
        );
        return Optional.ofNullable(entity);
    }

    public void persistNote(OwnerId ownerId, NoteEntity note) {
        requireOwner(ownerId, note.key().ownerId());
        entityManager.persist(note);
    }

    public void removeNote(OwnerId ownerId, NoteEntity note) {
        requireOwner(ownerId, note.key().ownerId());
        entityManager.remove(note);
    }

    public List<NoteEntity> trashedNotes(OwnerId ownerId, boolean lock) {
        var query = entityManager.createQuery(
            "select n from NoteEntity n where n.key.ownerId = :owner and n.deletedAt is not null " +
                "order by n.deletedAt, n.key.id", NoteEntity.class
        ).setParameter("owner", ownerId.value());
        if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList();
    }

    public List<NoteEntity> notes(OwnerId ownerId, NoteQuery filter, Cursor cursor, int limit) {
        StringBuilder sql = new StringBuilder("select n.id from notes n where n.owner_id = :owner");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("owner", ownerId.value());
        append(sql, parameters, "notebook", "n.notebook_id", filter.notebookId());
        append(sql, parameters, "type", "n.note_type", filter.type());
        append(sql, parameters, "pinned", "n.pinned", filter.pinned());
        if (filter.labelId() != null) {
            sql.append(" and exists (select 1 from note_labels nl where nl.owner_id = n.owner_id")
                .append(" and nl.note_id = n.id and nl.label_id = :label)");
            parameters.put("label", filter.labelId());
        }
        sql.append(switch (filter.archive()) {
            case ACTIVE -> " and n.archived = false";
            case ARCHIVED -> " and n.archived = true";
            case ALL -> "";
        });
        sql.append(switch (filter.trash()) {
            case ACTIVE -> " and n.deleted_at is null";
            case TRASHED -> " and n.deleted_at is not null";
            case ALL -> "";
        });
        if (cursor != null) {
            sql.append(" and ((n.pinned = :cursorPinned and (n.updated_at < :cursorUpdated")
                .append(" or (n.updated_at = :cursorUpdated and n.id > :cursorId)))")
                .append(" or (:cursorPinned = true and n.pinned = false))");
            parameters.put("cursorPinned", cursor.pinned());
            parameters.put("cursorUpdated", cursor.updatedAt());
            parameters.put("cursorId", cursor.id());
        }
        sql.append(" order by n.pinned desc, n.updated_at desc, n.id asc");
        Query query = entityManager.createNativeQuery(sql.toString()).setMaxResults(limit);
        parameters.forEach(query::setParameter);
        List<?> ids = query.getResultList();
        List<NoteEntity> result = new ArrayList<>(ids.size());
        for (Object id : ids) {
            result.add(entityManager.find(NoteEntity.class,
                new OwnedEntityId(ownerId.value(), (UUID) id)));
        }
        return result;
    }

    public List<ChecklistItemEntity> checklistItems(OwnerId ownerId, UUID noteId, boolean lock) {
        var query = entityManager.createQuery(
            "select i from ChecklistItemEntity i where i.key.ownerId = :owner and i.noteId = :note " +
                "order by i.sortOrder, i.key.id", ChecklistItemEntity.class
        ).setParameter("owner", ownerId.value()).setParameter("note", noteId);
        if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList();
    }

    public Optional<ChecklistItemEntity> checklistItem(OwnerId ownerId, UUID id) {
        return Optional.ofNullable(entityManager.find(
            ChecklistItemEntity.class, new OwnedEntityId(ownerId.value(), id)
        ));
    }

    public void persistChecklistItem(OwnerId ownerId, ChecklistItemEntity item) {
        requireOwner(ownerId, item.key().ownerId());
        entityManager.persist(item);
    }

    public void removeChecklistItem(OwnerId ownerId, ChecklistItemEntity item) {
        requireOwner(ownerId, item.key().ownerId());
        entityManager.remove(item);
    }

    public List<LabelEntity> labels(OwnerId ownerId, boolean lock) {
        var query = entityManager.createQuery(
            "select l from LabelEntity l where l.key.ownerId = :owner order by l.nameNormalized, l.key.id",
            LabelEntity.class
        ).setParameter("owner", ownerId.value());
        if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList();
    }

    public Optional<LabelEntity> label(OwnerId ownerId, UUID id, boolean lock) {
        LabelEntity entity = entityManager.find(
            LabelEntity.class, new OwnedEntityId(ownerId.value(), id),
            lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE
        );
        return Optional.ofNullable(entity);
    }

    public Optional<LabelEntity> labelByNormalizedName(OwnerId ownerId, String name) {
        return entityManager.createQuery(
            "select l from LabelEntity l where l.key.ownerId = :owner and l.nameNormalized = :name",
            LabelEntity.class
        ).setParameter("owner", ownerId.value()).setParameter("name", name)
            .getResultStream().findFirst();
    }

    public void persistLabel(OwnerId ownerId, LabelEntity label) {
        requireOwner(ownerId, label.key().ownerId());
        entityManager.persist(label);
    }

    public void removeLabel(OwnerId ownerId, LabelEntity label) {
        requireOwner(ownerId, label.key().ownerId());
        entityManager.remove(label);
    }

    @SuppressWarnings("unchecked")
    public List<UUID> noteLabelIds(OwnerId ownerId, UUID noteId) {
        return entityManager.createNativeQuery(
            "select label_id from note_labels where owner_id = :owner and note_id = :note order by label_id"
        ).setParameter("owner", ownerId.value()).setParameter("note", noteId).getResultList();
    }

    public void replaceNoteLabels(OwnerId ownerId, UUID noteId, Set<UUID> labelIds) {
        entityManager.createNativeQuery("delete from note_labels where owner_id = :owner and note_id = :note")
            .setParameter("owner", ownerId.value()).setParameter("note", noteId).executeUpdate();
        for (UUID labelId : labelIds) {
            entityManager.createNativeQuery(
                "insert into note_labels(owner_id, note_id, label_id) values (:owner, :note, :label)"
            ).setParameter("owner", ownerId.value()).setParameter("note", noteId)
                .setParameter("label", labelId).executeUpdate();
        }
    }

    public int moveNotebookNotes(OwnerId ownerId, UUID from, UUID to, boolean trash, Instant now) {
        String sql = "update notes set notebook_id = :to, updated_at = :now, version = version + 1" +
            (trash ? ", deleted_at = coalesce(deleted_at, :now)" : "") +
            " where owner_id = :owner and notebook_id = :from";
        return entityManager.createNativeQuery(sql).setParameter("to", to).setParameter("now", now)
            .setParameter("owner", ownerId.value()).setParameter("from", from).executeUpdate();
    }

    public void persistTombstone(OwnerId ownerId, TombstoneEntity tombstone) {
        requireOwner(ownerId, tombstone.ownerId());
        entityManager.persist(tombstone);
    }

    public boolean hasTombstone(OwnerId ownerId, String entityType, UUID entityId) {
        Number count = (Number) entityManager.createNativeQuery(
            "select count(*) from tombstones where owner_id = :owner " +
                "and entity_type = :type and entity_id = :entity"
        ).setParameter("owner", ownerId.value()).setParameter("type", entityType)
            .setParameter("entity", entityId).getSingleResult();
        return count.longValue() > 0;
    }

    public void flush(OwnerId ownerId) {
        entityManager.flush();
    }

    private static void append(StringBuilder sql, Map<String, Object> parameters,
                               String name, String column, Object value) {
        if (value != null) {
            sql.append(" and ").append(column).append(" = :").append(name);
            parameters.put(name, value);
        }
    }

    private static void requireOwner(OwnerId ownerId, UUID entityOwnerId) {
        if (!ownerId.value().equals(entityOwnerId)) {
            throw new IllegalArgumentException("Owner scope does not match entity owner");
        }
    }

    public record NoteQuery(UUID notebookId, UUID labelId, String type, Boolean pinned,
                            CollectionState archive, TrashState trash) {}
    public record Cursor(boolean pinned, Instant updatedAt, UUID id) {}
    public enum CollectionState { ACTIVE, ARCHIVED, ALL }
    public enum TrashState { ACTIVE, TRASHED, ALL }
}
