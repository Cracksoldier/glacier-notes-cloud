package com.glaciernotes.cloud.application.operations;

import com.glaciernotes.cloud.domain.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class CleanupService {
    private final EntityManager entityManager;
    private final TimeProvider time;

    public CleanupService(EntityManager entityManager, TimeProvider time) {
        this.entityManager = entityManager;
        this.time = time;
    }

    @Transactional
    public void expireInvitations() {
        entityManager.createNativeQuery("""
            update invitations set status='EXPIRED'
            where status='PENDING' and expires_at <= :now
            """).setParameter("now", time.now()).executeUpdate();
    }

    @Transactional
    public void removePasswordResetTokens() { removeTokens("PASSWORD_RESET"); }

    @Transactional
    public void removeEmailChangeTokens() { removeTokens("EMAIL_CHANGE"); }

    private void removeTokens(String type) {
        entityManager.createNativeQuery("""
            delete from security_tokens where token_type=:type
              and (expires_at <= :now or consumed_at is not null or revoked_at is not null)
            """).setParameter("type", type).setParameter("now", time.now()).executeUpdate();
    }

    @Transactional
    public void removeSessions() {
        entityManager.createNativeQuery("""
            delete from user_sessions where expires_at <= :now or revoked_at is not null
            """).setParameter("now", time.now()).executeUpdate();
    }

    @Transactional
    public void removeTombstones() {
        entityManager.createNativeQuery("delete from tombstones where expires_at <= :now")
            .setParameter("now", time.now()).executeUpdate();
    }

    @Transactional
    public void removeAuditEvents() {
        int retentionDays = ((Number) entityManager.createNativeQuery(
            "select audit_retention_days from instance_settings").getSingleResult()).intValue();
        entityManager.createNativeQuery("delete from audit_events where occurred_at < :cutoff")
            .setParameter("cutoff", time.now().minus(retentionDays, ChronoUnit.DAYS))
            .executeUpdate();
    }
}
