package com.glaciernotes.cloud.persistence;

import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.SystemContentScope;
import com.glaciernotes.cloud.domain.note.Note;
import com.glaciernotes.cloud.domain.note.NoteRepository;
import com.glaciernotes.cloud.domain.notebook.Notebook;
import com.glaciernotes.cloud.domain.notebook.NotebookRepository;
import com.glaciernotes.cloud.persistence.entity.ImageAssetEntity;
import com.glaciernotes.cloud.persistence.entity.NotebookEntity;
import com.glaciernotes.cloud.persistence.entity.OwnedEntityId;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository;
import com.glaciernotes.cloud.persistence.repository.ImageAssetRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OwnershipRepositoryTest {
    @Inject
    EntityManager entityManager;

    @Inject
    NotebookRepository notebooks;

    @Inject
    NoteRepository notes;

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
        notebooks.save(ownerA, notebook(ownerA, portableId, "Alpha notebook", now));
        notebooks.save(ownerB, notebook(ownerB, portableId, "Beta notebook", now));

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
    void legacySaveContractsRequireAnExplicitOwnerScope() throws Exception {
        assertEquals(
            Notebook.class,
            NotebookRepository.class.getMethod("save", OwnerId.class, Notebook.class).getReturnType()
        );
        assertEquals(
            Note.class,
            NoteRepository.class.getMethod("save", OwnerId.class, Note.class).getReturnType()
        );
    }

    @Test
    @TestTransaction
    void ownedEntityIdentifiersRemainComparableDuringJpaHydration() throws Exception {
        Instant now = Instant.parse("2026-07-24T12:00:00Z");
        OwnerId owner = new OwnerId(UUID.randomUUID());
        UUID notebookId = UUID.randomUUID();
        persistUser(owner, "hydration-" + notebookId, now);
        notebooks.save(owner, notebook(owner, notebookId, "Hydrated", now));
        entityManager.flush();
        entityManager.clear();

        NotebookEntity hydrated = entityManager.find(
            NotebookEntity.class,
            new OwnedEntityId(owner.value(), notebookId)
        );
        OwnedEntityId expected = new OwnedEntityId(owner.value(), notebookId);
        assertNotNull(hydrated);
        assertEquals(expected, hydrated.key());
        assertEquals(expected.hashCode(), hydrated.key().hashCode());
        assertNotEquals(
            new OwnedEntityId(owner.value(), UUID.randomUUID()),
            hydrated.key()
        );

        Constructor<OwnedEntityId> constructor = OwnedEntityId.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        OwnedEntityId left = constructor.newInstance();
        OwnedEntityId right = constructor.newInstance();

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    void legacySavesRejectAnOwnerScopeMismatch() {
        Instant now = Instant.parse("2026-07-24T12:00:00Z");
        OwnerId entityOwner = new OwnerId(UUID.randomUUID());
        OwnerId differentScope = new OwnerId(UUID.randomUUID());
        UUID notebookId = UUID.randomUUID();
        Notebook notebook = notebook(entityOwner, notebookId, "Mismatch", now);
        Note note = new Note(
            entityOwner, UUID.randomUUID(), notebookId, "text", "Mismatch", "",
            false, false, null, null, now, now, 0
        );

        assertThrows(IllegalArgumentException.class, () -> notebooks.save(differentScope, notebook));
        assertThrows(IllegalArgumentException.class, () -> notes.save(differentScope, note));
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
        notebooks.save(owner, notebook(owner, notebookId, "Garbage test", base));
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
