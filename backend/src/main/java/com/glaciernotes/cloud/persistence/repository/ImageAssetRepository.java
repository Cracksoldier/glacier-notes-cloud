package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.SystemContentScope;
import com.glaciernotes.cloud.persistence.entity.ImageAssetEntity;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.OwnedEntityId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ImageAssetRepository {
    private final EntityManager entityManager;
    public ImageAssetRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public Optional<ImageAssetEntity> find(OwnerId owner, UUID id, boolean lock) {
        return Optional.ofNullable(entityManager.find(ImageAssetEntity.class,
            new OwnedEntityId(owner.value(), id), lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE));
    }

    public void lockOwner(OwnerId owner) {
        entityManager.createNativeQuery("select id from app_users where id = :owner for update")
            .setParameter("owner", owner.value()).getSingleResult();
    }

    public InstanceSettingsEntity settings() { return entityManager.find(InstanceSettingsEntity.class, (short) 1); }

    public long usedBytes(OwnerId owner) {
        return ((Number) entityManager.createNativeQuery(
            "select coalesce(sum(byte_size + coalesce(thumbnail_byte_size, 0)), 0) from image_assets where owner_id = :owner")
            .setParameter("owner", owner.value()).getSingleResult()).longValue();
    }

    public long count(OwnerId owner) {
        return ((Number) entityManager.createNativeQuery("select count(*) from image_assets where owner_id = :owner")
            .setParameter("owner", owner.value()).getSingleResult()).longValue();
    }

    public void persist(OwnerId owner, ImageAssetEntity asset) {
        requireOwner(owner, asset);
        entityManager.persist(asset);
    }

    public void remove(OwnerId owner, ImageAssetEntity asset) {
        requireOwner(owner, asset);
        entityManager.remove(asset);
    }

    public boolean referenced(OwnerId owner, UUID id) {
        return ((Number) entityManager.createNativeQuery("""
            select (select count(*) from note_image_references where owner_id = :owner and image_id = :id)
                 + (select count(*) from note_version_image_references where owner_id = :owner and image_id = :id)
            """).setParameter("owner", owner.value()).setParameter("id", id).getSingleResult()).longValue() > 0;
    }

    public List<ImageAssetEntity> garbage(SystemContentScope scope, Instant cutoff) {
        if (scope != SystemContentScope.BACKGROUND_MAINTENANCE) {
            throw new IllegalArgumentException("System-wide image access requires maintenance scope");
        }
        return entityManager.createNativeQuery("""
            select i.* from image_assets i
            where i.orphaned_at is not null and i.orphaned_at < :cutoff
              and not exists (
                select 1 from note_image_references r
                where r.owner_id=i.owner_id and r.image_id=i.id
              )
              and not exists (
                select 1 from note_version_image_references r
                where r.owner_id=i.owner_id and r.image_id=i.id
              )
            order by i.orphaned_at,i.owner_id,i.id
            limit 100
            """, ImageAssetEntity.class).setParameter("cutoff", cutoff).getResultList();
    }

    public void flush() { entityManager.flush(); }

    private void requireOwner(OwnerId owner, ImageAssetEntity asset) {
        if (!asset.key().ownerId().equals(owner.value())) {
            throw new IllegalArgumentException("Image asset owner does not match repository scope");
        }
    }
}
