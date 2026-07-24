package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.notebook.Notebook;
import com.glaciernotes.cloud.domain.notebook.NotebookRepository;
import com.glaciernotes.cloud.persistence.entity.NotebookEntity;
import com.glaciernotes.cloud.persistence.entity.OwnedEntityId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaNotebookRepository implements NotebookRepository {
    private final EntityManager entityManager;

    public JpaNotebookRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Notebook> findById(OwnerId ownerId, UUID id) {
        return Optional.ofNullable(
            entityManager.find(NotebookEntity.class, new OwnedEntityId(ownerId.value(), id))
        ).map(NotebookEntity::toDomain);
    }

    @Override
    public List<Notebook> list(OwnerId ownerId) {
        return entityManager.createQuery(
                "from NotebookEntity n where n.key.ownerId = :ownerId order by n.sortOrder, n.key.id",
                NotebookEntity.class
            )
            .setParameter("ownerId", ownerId.value())
            .getResultStream()
            .map(NotebookEntity::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public Notebook save(OwnerId ownerId, Notebook notebook) {
        requireOwner(ownerId, notebook.ownerId());
        var key = new OwnedEntityId(ownerId.value(), notebook.id());
        var entity = entityManager.find(NotebookEntity.class, key);
        if (entity == null) {
            entity = new NotebookEntity(notebook);
            entityManager.persist(entity);
        } else {
            entity.update(notebook);
        }
        entityManager.flush();
        return entity.toDomain();
    }

    private static void requireOwner(OwnerId scope, OwnerId entityOwner) {
        if (!scope.equals(entityOwner)) {
            throw new IllegalArgumentException("Owner scope does not match entity owner");
        }
    }
}
