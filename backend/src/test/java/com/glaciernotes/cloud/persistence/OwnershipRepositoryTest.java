package com.glaciernotes.cloud.persistence;

import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.SystemContentScope;
import com.glaciernotes.cloud.domain.notebook.Notebook;
import com.glaciernotes.cloud.domain.notebook.NotebookRepository;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.persistence.entity.ImageAssetEntity;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository;
import com.glaciernotes.cloud.persistence.repository.ImageAssetRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class OwnershipRepositoryTest {
    @Inject
    EntityManager entityManager;

    @Inject
    NotebookRepository notebooks;

    @Inject
    CoreContentRepository coreContent;

    @Inject
    ImageAssetRepository images;

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
        assertEquals("Alpha notebook", coreContent.notebook(ownerA, portableId, false).orElseThrow().name());
        assertEquals("Beta notebook", coreContent.notebook(ownerB, portableId, false).orElseThrow().name());
        assertTrue(coreContent.notebook(new OwnerId(UUID.randomUUID()), portableId, false).isEmpty());
    }

    @Test
    void imageMutationsRejectAnOwnerScopeMismatch() {
        var now = Instant.parse("2026-07-24T12:00:00Z");
        var asset = new ImageAssetEntity(UUID.randomUUID(), UUID.randomUUID(), "image/jpeg", "photo.jpg",
            10, 1, 1, "hash", "FILESYSTEM", "asset-image",
            "image/jpeg", 5, 1, 1, "asset-thumbnail", now);
        OwnerId differentOwner = new OwnerId(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> images.persist(differentOwner, asset));
        assertThrows(IllegalArgumentException.class, () -> images.remove(differentOwner, asset));
    }

    @Test
    @TestTransaction
    void garbageSelectionFiltersReferencesBeforeApplyingItsBatchLimit() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        OwnerId owner = new OwnerId(UUID.randomUUID());
        persistUser(owner, "garbage", base);
        UUID notebookId = UUID.randomUUID();
        notebooks.save(notebook(owner, notebookId, "Garbage test", base));
        UUID noteId = UUID.randomUUID();
        entityManager.createNativeQuery("""
            insert into notes(owner_id,id,notebook_id,note_type,title,content)
            values (:owner,:id,:notebook,'text','References','')
            """).setParameter("owner", owner.value()).setParameter("id", noteId)
            .setParameter("notebook", notebookId).executeUpdate();

        for (int index = 0; index < 101; index++) {
            ImageAssetEntity referenced = asset(owner, base.plusSeconds(index));
            images.persist(owner, referenced);
            entityManager.flush();
            entityManager.createNativeQuery("""
                insert into note_image_references(owner_id,note_id,image_id,sort_order)
                values (:owner,:note,:image,:sort)
                """).setParameter("owner", owner.value()).setParameter("note", noteId)
                .setParameter("image", referenced.id()).setParameter("sort", index).executeUpdate();
        }
        ImageAssetEntity eligible = asset(owner, base.plusSeconds(102));
        images.persist(owner, eligible);
        entityManager.flush();

        var garbage = images.garbage(SystemContentScope.BACKGROUND_MAINTENANCE,
            base.plusSeconds(1000));

        assertEquals(1, garbage.size());
        assertEquals(eligible.id(), garbage.getFirst().id());
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

    private ImageAssetEntity asset(OwnerId owner, Instant now) {
        UUID id = UUID.randomUUID();
        return new ImageAssetEntity(owner.value(), id, "image/jpeg", "photo.jpg",
            10, 1, 1, id.toString(), "FILESYSTEM", "assets/" + id + "/image",
            "image/jpeg", 5, 1, 1, "assets/" + id + "/thumbnail", now);
    }
}
