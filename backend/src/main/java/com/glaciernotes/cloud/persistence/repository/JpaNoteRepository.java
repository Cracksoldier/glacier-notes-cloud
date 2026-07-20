package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.note.Note;
import com.glaciernotes.cloud.domain.note.NoteRepository;
import com.glaciernotes.cloud.persistence.entity.NoteEntity;
import com.glaciernotes.cloud.persistence.entity.OwnedEntityId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaNoteRepository implements NoteRepository {
    private final EntityManager entityManager;

    public JpaNoteRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Note> findById(OwnerId ownerId, UUID id) {
        return Optional.ofNullable(
            entityManager.find(NoteEntity.class, new OwnedEntityId(ownerId.value(), id))
        ).map(NoteEntity::toDomain);
    }

    @Override
    public List<Note> listByNotebook(OwnerId ownerId, UUID notebookId, int limit) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        return entityManager.createQuery(
                "from NoteEntity n where n.key.ownerId = :ownerId and n.notebookId = :notebookId "
                    + "order by n.updatedAt desc, n.key.id",
                NoteEntity.class
            )
            .setParameter("ownerId", ownerId.value())
            .setParameter("notebookId", notebookId)
            .setMaxResults(limit)
            .getResultStream()
            .map(NoteEntity::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public Note save(Note note) {
        var key = new OwnedEntityId(note.ownerId().value(), note.id());
        var entity = entityManager.find(NoteEntity.class, key);
        if (entity == null) {
            entity = new NoteEntity(note);
            entityManager.persist(entity);
        } else {
            entity.update(note);
        }
        entityManager.flush();
        return entity.toDomain();
    }
}

