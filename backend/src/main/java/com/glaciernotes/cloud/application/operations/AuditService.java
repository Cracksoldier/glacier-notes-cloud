package com.glaciernotes.cloud.application.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.generated.model.AuditEvent;
import com.glaciernotes.cloud.generated.model.AuditEventPage;
import com.glaciernotes.cloud.generated.model.PageMetadata;
import com.glaciernotes.cloud.persistence.entity.AuditEventEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class AuditService {
    private static final Set<String> FORBIDDEN_METADATA = Set.of(
        "password", "token", "secret", "note", "content", "filename", "smtpcredential"
    );
    private final EntityManager entityManager;
    private final IdGenerator ids;
    private final TimeProvider time;
    private final RequestAuditContext request;
    private final ObjectMapper objectMapper;

    public AuditService(EntityManager entityManager, IdGenerator ids, TimeProvider time,
                        RequestAuditContext request, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.ids = ids;
        this.time = time;
        this.request = request;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String type, UUID actor, UUID target, String entityType, UUID entityId,
                       String result, String correlationId, Map<String, String> metadata) {
        entityManager.persist(AuditEventEntity.administrative(ids.nextId(), type, actor, target,
            entityType, entityId, time.now(), correlationId, safeMetadata(metadata), request.address(),
            request.clientDescription(), result));
    }

    @Transactional
    public void recordBackground(String type, UUID actor, String entityType, UUID entityId,
                                 String result, String correlationId, Map<String, String> metadata) {
        entityManager.persist(AuditEventEntity.administrative(ids.nextId(), type, actor, null,
            entityType, entityId, time.now(), correlationId, safeMetadata(metadata), null, null, result));
    }

    @Transactional
    public AuditEventPage list(String eventType, String result, OffsetDateTime from, OffsetDateTime to,
                               String cursor, int limit) {
        int offset = decode(cursor);
        List<AuditEventEntity> rows = query(eventType, result, from, to, offset, limit + 1);
        boolean hasNext = rows.size() > limit;
        List<AuditEvent> items = rows.stream().limit(limit).map(this::model).toList();
        String next = hasNext ? encode(offset + items.size()) : null;
        return new AuditEventPage().items(items).page(new PageMetadata()
            .size(items.size()).hasNext(hasNext).nextCursor(next));
    }

    @Transactional
    public File export(String format, String eventType, String result, OffsetDateTime from,
                       OffsetDateTime to) {
        if (!Set.of("csv", "json").contains(format)) {
            throw OperationalFailure.invalid("Export format must be csv or json.");
        }
        List<AuditEvent> events = query(eventType, result, from, to, 0, 100_000).stream()
            .map(this::model).toList();
        try {
            File output = Files.createTempFile("glacier-audit-", "." + format).toFile();
            output.deleteOnExit();
            if ("json".equals(format)) objectMapper.writeValue(output, events);
            else writeCsv(output, events);
            return output;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not create audit export", failure);
        }
    }

    private List<AuditEventEntity> query(String eventType, String result, OffsetDateTime from,
                                         OffsetDateTime to, int offset, int limit) {
        String normalizedEventType = blank(eventType);
        String normalizedResult = blank(result);
        StringBuilder jpql = new StringBuilder("select a from AuditEventEntity a where 1=1");
        if (normalizedEventType != null) jpql.append(" and a.eventType = :eventType");
        if (normalizedResult != null) jpql.append(" and a.result = :result");
        if (from != null) jpql.append(" and a.occurredAt >= :fromTime");
        if (to != null) jpql.append(" and a.occurredAt <= :toTime");
        jpql.append(" order by a.occurredAt desc, a.id desc");

        TypedQuery<AuditEventEntity> query = entityManager.createQuery(jpql.toString(),
            AuditEventEntity.class);
        if (normalizedEventType != null) query.setParameter("eventType", normalizedEventType);
        if (normalizedResult != null) query.setParameter("result", normalizedResult);
        if (from != null) query.setParameter("fromTime", from.toInstant());
        if (to != null) query.setParameter("toTime", to.toInstant());
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    private AuditEvent model(AuditEventEntity value) {
        return new AuditEvent().id(value.id()).eventType(value.eventType())
            .occurredAt(value.occurredAt().atOffset(ZoneOffset.UTC))
            .actorUserId(value.actorUserId()).targetUserId(value.targetUserId())
            .targetEntityType(value.targetEntityType()).targetEntityId(value.targetEntityId())
            .result(AuditEvent.ResultEnum.fromValue(value.result()))
            .ipAddress(value.ipAddress() == null ? null : value.ipAddress().getHostAddress())
            .clientDescription(value.clientDescription()).correlationId(value.correlationId())
            .metadata(value.metadataJson());
    }

    private void writeCsv(File output, List<AuditEvent> events) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output.toPath())) {
            writer.write("id,eventType,occurredAt,actorUserId,targetUserId,targetEntityType,targetEntityId,result,ipAddress,clientDescription,correlationId,metadata");
            writer.newLine();
            for (AuditEvent event : events) {
                List<String> fields = List.of(
                    text(event.getId()), text(event.getEventType()), text(event.getOccurredAt()),
                    text(event.getActorUserId()), text(event.getTargetUserId()),
                    text(event.getTargetEntityType()), text(event.getTargetEntityId()),
                    text(event.getResult()), text(event.getIpAddress()),
                    text(event.getClientDescription()), text(event.getCorrelationId()),
                    objectMapper.writeValueAsString(event.getMetadata())
                );
                writer.write(fields.stream().map(this::csv).collect(java.util.stream.Collectors.joining(",")));
                writer.newLine();
            }
        }
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String text(Object value) { return value == null ? "" : value.toString(); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value; }
    private Map<String, String> safeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return Map.of();
        return metadata.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .filter(entry -> FORBIDDEN_METADATA.stream().noneMatch(forbidden ->
                entry.getKey().toLowerCase(Locale.ROOT).contains(forbidden)))
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    private String encode(int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Integer.toString(offset).getBytes());
    }
    private int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            int value = Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor)));
            if (value < 0 || value > 1_000_000) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException failure) {
            throw OperationalFailure.invalid("Audit cursor is invalid.");
        }
    }
}
