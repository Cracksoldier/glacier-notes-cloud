package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.net.InetAddress;
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
    @Column(name = "ip_address", columnDefinition = "inet")
    private InetAddress ipAddress;
    @Column(name = "client_description")
    private String clientDescription;
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

    public static AuditEventEntity authenticationFailed(
        UUID id,
        UUID targetUserId,
        Instant now,
        InetAddress ipAddress,
        String clientDescription,
        String correlationId
    ) {
        var event = new AuditEventEntity();
        event.id = id;
        event.eventType = "LOGIN_FAILED";
        event.occurredAt = now;
        event.targetUserId = targetUserId;
        event.targetEntityType = targetUserId == null ? null : "USER";
        event.targetEntityId = targetUserId;
        event.result = "FAILURE";
        event.ipAddress = ipAddress;
        event.clientDescription = clientDescription;
        event.correlationId = correlationId;
        event.metadataJson = Map.of();
        return event;
    }

    public static AuditEventEntity administrative(
        UUID id, String type, UUID actorId, UUID targetUserId, String entityType,
        UUID entityId, Instant now, String correlationId, Map<String, String> metadata
    ) {
        var event = new AuditEventEntity();
        event.id = id;
        event.eventType = type;
        event.occurredAt = now;
        event.actorUserId = actorId;
        event.targetUserId = targetUserId;
        event.targetEntityType = entityType;
        event.targetEntityId = entityId;
        event.result = "SUCCESS";
        event.correlationId = correlationId;
        event.metadataJson = Map.copyOf(metadata);
        return event;
    }

    public static AuditEventEntity administrative(
        UUID id, String type, UUID actorId, UUID targetUserId, String entityType,
        UUID entityId, Instant now, String correlationId, Map<String, String> metadata,
        InetAddress address, String clientDescription, String result
    ) {
        var event = administrative(id, type, actorId, targetUserId, entityType, entityId,
            now, correlationId, metadata);
        event.ipAddress = address;
        event.clientDescription = clientDescription;
        event.result = result;
        return event;
    }

    public UUID id() { return id; }
    public String eventType() { return eventType; }
    public Instant occurredAt() { return occurredAt; }
    public UUID actorUserId() { return actorUserId; }
    public UUID targetUserId() { return targetUserId; }
    public String targetEntityType() { return targetEntityType; }
    public UUID targetEntityId() { return targetEntityId; }
    public String result() { return result; }
    public InetAddress ipAddress() { return ipAddress; }
    public String clientDescription() { return clientDescription; }
    public String correlationId() { return correlationId; }
    public Map<String, String> metadataJson() { return metadataJson; }
}
