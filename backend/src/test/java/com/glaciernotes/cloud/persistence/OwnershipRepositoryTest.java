package com.glaciernotes.cloud.persistence;

import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.notebook.Notebook;
import com.glaciernotes.cloud.domain.notebook.NotebookRepository;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OwnershipRepositoryTest {
    @Inject
    EntityManager entityManager;

    @Inject
    NotebookRepository notebooks;

    @Test
    @TestTransaction
    void identicalPortableUuidCanExistForTwoOwnersWithoutCrossOwnerReads() {
        var now = Instant.parse("2026-07-20T19:00:00Z");
        var ownerA = new OwnerId(UUID.randomUUID());
        var ownerB = new OwnerId(UUID.randomUUID());
        persistUser(ownerA, "alpha", now);
        persistUser(ownerB, "beta", now);

        var portableId = UUID.randomUUID();
        notebooks.save(notebook(ownerA, portableId, "Alpha notebook", now));
        notebooks.save(notebook(ownerB, portableId, "Beta notebook", now));

        assertEquals("Alpha notebook", notebooks.findById(ownerA, portableId).orElseThrow().name());
        assertEquals("Beta notebook", notebooks.findById(ownerB, portableId).orElseThrow().name());
        assertEquals(1, notebooks.list(ownerA).size());
        assertEquals(1, notebooks.list(ownerB).size());
        assertTrue(notebooks.findById(new OwnerId(UUID.randomUUID()), portableId).isEmpty());
    }

    private void persistUser(OwnerId ownerId, String name, Instant now) {
        entityManager.persist(new UserEntity(
            ownerId.value(), name, name, name + "@example.test", name + "@example.test",
            "USER", "ACTIVE", now
        ));
    }

    private Notebook notebook(OwnerId owner, UUID id, String name, Instant now) {
        return new Notebook(owner, id, name, null, true, 0, now, now, 0);
    }
}

