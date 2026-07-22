package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.domain.OwnerId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class UserUsageRepository {
    private final EntityManager entityManager;

    public UserUsageRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public UserUsage summarize(OwnerId ownerId) {
        var counts = (Object[]) entityManager.createNativeQuery("""
            select
              coalesce((select sum(byte_size + coalesce(thumbnail_byte_size, 0)) from image_assets where owner_id = ?1), 0),
              (select count(*) from notes where owner_id = ?1),
              (select count(*) from notebooks where owner_id = ?1),
              (select count(*) from image_assets where owner_id = ?1)
            """).setParameter(1, ownerId.value()).getSingleResult();
        return new UserUsage(number(counts[0]), number(counts[1]), number(counts[2]), number(counts[3]));
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    public record UserUsage(long storageBytes, long noteCount, long notebookCount, long imageCount) {}
}
