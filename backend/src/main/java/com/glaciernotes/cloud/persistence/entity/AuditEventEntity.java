package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {
    @Id
    private UUID id;
    @Column(name = "event_type")
    private String eventType;
    @Column(name = "occurred_at")
    private Instant occurredAt;
    @Column(name = "actor_user_id")
    private UUID actorUserId;
    @Column(name = "target_user_id")
    private UUID targetUserId;
    @Column(name = "target_entity_type")
    private String targetEntityType;
    @Column(name = "target_entity_id")
    private UUID targetEntityId;
    private String result;
    @Column(name = "correlation_id")
    private String correlationId;
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> metadataJson;

    protected AuditEventEntity() {
    }

    public static AuditEventEntity instanceBootstrapped(
        UUID id,
        UUID administratorId,
        Instant now,
        String correlationId
    ) {
        var event = new AuditEventEntity();
        event.id = id;
        event.eventType = "INSTANCE_BOOTSTRAPPED";
        event.occurredAt = now;
        event.actorUserId = administratorId;
        event.targetUserId = administratorId;
        event.targetEntityType = "USER";
        event.targetEntityId = administratorId;
        event.result = "SUCCESS";
        event.correlationId = correlationId;
        event.metadataJson = Map.of("source", "setup");
        return event;
    }
}
