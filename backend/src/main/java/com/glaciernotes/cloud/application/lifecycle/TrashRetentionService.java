package com.glaciernotes.cloud.application.lifecycle;

import com.glaciernotes.cloud.application.content.ContentService;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.TimeProvider;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TrashRetentionService {
    private static final Logger LOG = Logger.getLogger(TrashRetentionService.class);
    private final EntityManager entityManager;
    private final ContentService content;
    private final TimeProvider time;

    public TrashRetentionService(EntityManager entityManager, ContentService content, TimeProvider time) {
        this.entityManager = entityManager;
        this.content = content;
        this.time = time;
    }

    public void purgeExpiredTrash() {
        @SuppressWarnings("unchecked")
        List<Object[]> expired = entityManager.createNativeQuery("""
            select n.owner_id, n.id, n.version
            from notes n
            join user_settings us on us.user_id = n.owner_id
            cross join instance_settings ins
            where n.deleted_at is not null
              and (us.trash_auto_purge_days is not null or not ins.users_may_disable_auto_purge)
              and n.deleted_at + make_interval(days => coalesce(
                    us.trash_auto_purge_days, ins.default_trash_retention_days)) <= :now
            order by n.deleted_at, n.owner_id, n.id
            limit 500
            """).setParameter("now", time.now()).getResultList();
        for (Object[] row : expired) {
            try {
                content.purgeNote(new OwnerId((UUID) row[0]), (UUID) row[1], ((Number) row[2]).longValue());
            } catch (RuntimeException failure) {
                LOG.warnf("Trash retention skipped note=%s category=%s", row[1],
                    failure.getClass().getSimpleName());
            }
        }
    }
}
