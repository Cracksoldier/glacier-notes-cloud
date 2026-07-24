package com.glaciernotes.cloud.application.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.glaciernotes.cloud.application.image.ImageProcessor;
import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import com.glaciernotes.cloud.application.storage.ExternalStorageOperations;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.TombstoneEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class TransferApplyService {
    private static final Pattern IMAGE_REFERENCE = Pattern.compile("glacier-img://([0-9a-fA-F-]{36})");
    private final PortableTransferCodec codec;
    private final EntityManager em;
    private final ImageProcessor images;
    private final BinaryAssetStorage storage;
    private final IdGenerator ids;
    private final TimeProvider clock;
    private final ExternalStorageOperations operations;

    public TransferApplyService(PortableTransferCodec codec, EntityManager em, ImageProcessor images,
                                BinaryAssetStorage storage, IdGenerator ids, TimeProvider clock,
                                ExternalStorageOperations operations) {
        this.codec = codec; this.em = em; this.images = images; this.storage = storage;
        this.ids = ids; this.clock = clock; this.operations = operations;
    }

    @Transactional
    public void apply(UUID jobId, Path input, UUID owner, String strategy,
                      PortableTransferCodec.Inspection inspection,
                      PortableTransferCodec.Cancellation cancellation) throws IOException {
        em.createNativeQuery("select id from app_users where id=:owner for update")
            .setParameter("owner", owner).getSingleResult();
        if (strategy.equals("PRESERVE") && targetConflicts(owner, inspection))
            throw new IllegalStateException("The target content changed after inspection; inspect the file again.");
        IdMap mapping = new IdMap(jobId, strategy);
        List<UUID> reservations = new ArrayList<>();
        try {
            boolean exactRestore = strategy.equals("PRESERVE") && "all".equals(inspection.header().scopeKind)
                && inspection.header().defaultNotebookId != null && pristine(owner);
            if (exactRestore) em.createNativeQuery("delete from notebooks where owner_id=:owner")
                .setParameter("owner", owner).executeUpdate();
            codec.forEach(input, "notebooks", node -> notebook(owner, node, mapping, exactRestore,
                inspection.header().defaultNotebookId, cancellation));
            codec.forEach(input, "labels", node -> label(owner, node, mapping, cancellation));
            codec.forEachImage(input,
                image -> image(jobId, owner, image, mapping, reservations, cancellation));
            codec.forEach(input, "notes", node -> note(owner, node, mapping, cancellation));
            long used = ((Number) em.createNativeQuery("select coalesce(sum(byte_size + coalesce(thumbnail_byte_size,0)),0) from image_assets where owner_id=:owner")
                .setParameter("owner", owner).getSingleResult()).longValue();
            long quota = ((Number) em.createNativeQuery("select per_user_storage_quota_bytes from instance_settings where singleton_key=1")
                .getSingleResult()).longValue();
            if (used > quota) throw new IllegalStateException("The import exceeds the target user's storage quota.");
            em.flush();
        } catch (RuntimeException | IOException failure) {
            reservations.forEach(operations::expedite);
            throw failure;
        }
    }

    private void notebook(UUID owner, JsonNode node, IdMap map, boolean exact, UUID defaultId,
                          PortableTransferCodec.Cancellation cancel) {
        cancel.check(); UUID source = uuid(node, "id"), id = map.notebook(source);
        boolean isDefault = exact && source.equals(defaultId);
        em.createNativeQuery("""
            insert into notebooks(owner_id,id,name,color,is_default,sort_order,created_at,updated_at,version)
            values (:owner,:id,:name,:color,:default,:sort,:created,:updated,0)
            on conflict(owner_id,id) do update set name=excluded.name,color=excluded.color,
              sort_order=excluded.sort_order,updated_at=excluded.updated_at,version=notebooks.version+1
            """).setParameter("owner", owner).setParameter("id", id)
            .setParameter("name", node.path("name").textValue()).setParameter("color", storedColor(node))
            .setParameter("default", isDefault).setParameter("sort", node.path("sortOrder").intValue())
            .setParameter("created", instant(node, "createdAt")).setParameter("updated", instant(node, "updatedAt"))
            .executeUpdate();
        clearTombstone(owner, "NOTEBOOK", id);
    }

    private void label(UUID owner, JsonNode node, IdMap map, PortableTransferCodec.Cancellation cancel) {
        cancel.check(); UUID id = map.label(uuid(node, "id")); String name = node.path("name").textValue();
        Instant now = clock.now();
        em.createNativeQuery("""
            insert into labels(owner_id,id,name,name_normalized,created_at,updated_at,version)
            values (:owner,:id,:name,:normalized,:now,:now,0)
            on conflict(owner_id,id) do update set name=excluded.name,name_normalized=excluded.name_normalized,
              updated_at=excluded.updated_at,version=labels.version+1
            """).setParameter("owner", owner).setParameter("id", id).setParameter("name", name)
            .setParameter("normalized", name.strip().toLowerCase(Locale.ROOT)).setParameter("now", now).executeUpdate();
        clearTombstone(owner, "LABEL", id);
    }

    private void image(UUID jobId, UUID owner, PortableTransferCodec.DecodedImage image,
                       IdMap map, List<UUID> reservations,
                       PortableTransferCodec.Cancellation cancel) {
        JsonNode node = image.metadata();
        cancel.check(); UUID id = map.image(uuid(node, "id"));
        @SuppressWarnings("unchecked") List<Object[]> old = em.createNativeQuery(
                "select storage_key,thumbnail_storage_key,storage_backend,byte_size,coalesce(thumbnail_byte_size,0) "
                    + "from image_assets where owner_id=:owner and id=:id")
            .setParameter("owner", owner).setParameter("id", id).getResultList();
        long maximum = ((Number) em.createNativeQuery(
            "select maximum_image_bytes from instance_settings where singleton_key=1"
        ).getSingleResult()).longValue();
        try (var processed = images.process(image.content(), maximum)) {
            @SuppressWarnings("unchecked") List<String> allowed = em.createNativeQuery(
                "select unnest(allowed_upload_types) from instance_settings where singleton_key=1").getResultList();
            if (!allowed.contains(processed.sourceMimeType())) throw new IllegalStateException("An imported image type is disabled by the administrator.");
            String prefix = "assets/" + id + "/import-" + jobId;
            String main = prefix + "-image"; String thumb = prefix + "-thumbnail";
            long oldBytes = old.isEmpty() ? 0
                : ((Number) old.getFirst()[3]).longValue() + ((Number) old.getFirst()[4]).longValue();
            long newBytes = processed.byteSize() + processed.thumbnailByteSize();
            UUID reservationId = UUID.nameUUIDFromBytes(
                ("binary-reservation:" + jobId + ":" + id).getBytes(StandardCharsets.UTF_8));
            operations.reserveBinary(new OwnerId(owner), reservationId, jobId, storage.backend(),
                main, thumb, Math.max(0, newBytes - oldBytes));
            reservations.add(reservationId);
            storage.store(main, processed.main(), processed.byteSize(), processed.mimeType());
            storage.store(thumb, processed.thumbnail(), processed.thumbnailByteSize(), processed.mimeType());
            Instant now = clock.now();
            em.createNativeQuery("""
                insert into image_assets(owner_id,id,mime_type,original_file_name,byte_size,width,height,
                  content_hash,storage_backend,storage_key,thumbnail_mime_type,thumbnail_byte_size,
                  thumbnail_width,thumbnail_height,thumbnail_storage_key,orphaned_at,created_at,updated_at,version)
                values (:owner,:id,:mime,:name,:bytes,:width,:height,:hash,:backend,:key,:thumbMime,
                  :thumbBytes,:thumbWidth,:thumbHeight,:thumbKey,:now,:now,:now,0)
                on conflict(owner_id,id) do update set mime_type=excluded.mime_type,
                  original_file_name=excluded.original_file_name,byte_size=excluded.byte_size,
                  width=excluded.width,height=excluded.height,content_hash=excluded.content_hash,
                  storage_backend=excluded.storage_backend,storage_key=excluded.storage_key,
                  thumbnail_mime_type=excluded.thumbnail_mime_type,thumbnail_byte_size=excluded.thumbnail_byte_size,
                  thumbnail_width=excluded.thumbnail_width,thumbnail_height=excluded.thumbnail_height,
                  thumbnail_storage_key=excluded.thumbnail_storage_key,updated_at=excluded.updated_at,
                  version=image_assets.version+1
                """).setParameter("owner", owner).setParameter("id", id).setParameter("mime", processed.mimeType())
                .setParameter("name", node.path("fileName").isTextual() ? node.path("fileName").textValue() : null)
                .setParameter("bytes", processed.byteSize()).setParameter("width", processed.width())
                .setParameter("height", processed.height()).setParameter("hash", processed.hash())
                .setParameter("backend", storage.backend()).setParameter("key", main)
                .setParameter("thumbMime", processed.mimeType()).setParameter("thumbBytes", processed.thumbnailByteSize())
                .setParameter("thumbWidth", processed.thumbnailWidth()).setParameter("thumbHeight", processed.thumbnailHeight())
                .setParameter("thumbKey", thumb).setParameter("now", now).executeUpdate();
            if (!old.isEmpty()) {
                String oldMain = (String) old.getFirst()[0];
                String oldThumb = (String) old.getFirst()[1];
                String oldBackend = (String) old.getFirst()[2];
                if (!main.equals(oldMain) || !thumb.equals(oldThumb)) {
                    operations.enqueueBinaryDelete(new OwnerId(owner), oldBackend, oldMain, oldThumb);
                }
            }
            operations.completeReservation(reservationId);
            clearTombstone(owner, "IMAGE_ASSET", id);
        }
    }

    private void note(UUID owner, JsonNode node, IdMap map, PortableTransferCodec.Cancellation cancel) {
        cancel.check(); UUID source = uuid(node, "id"), id = map.note(source);
        UUID notebook = map.notebook(uuid(node, "notebookId")); String content = node.path("content").textValue();
        if (map.copy()) for (Map.Entry<UUID, UUID> entry : map.images.entrySet())
            content = content.replace(entry.getKey().toString(), entry.getValue().toString());
        Instant created = instant(node, "createdAt"), updated = instant(node, "updatedAt");
        em.createNativeQuery("""
            insert into notes(owner_id,id,notebook_id,note_type,title,content,pinned,archived,color,
              deleted_at,created_at,updated_at,version)
            values (:owner,:id,:notebook,:type,:title,:content,:pinned,:archived,:color,:deleted,:created,:updated,0)
            on conflict(owner_id,id) do update set notebook_id=excluded.notebook_id,note_type=excluded.note_type,
              title=excluded.title,content=excluded.content,pinned=excluded.pinned,archived=excluded.archived,
              color=excluded.color,deleted_at=excluded.deleted_at,updated_at=excluded.updated_at,
              version=notes.version+1
            """).setParameter("owner", owner).setParameter("id", id).setParameter("notebook", notebook)
            .setParameter("type", node.path("type").textValue()).setParameter("title", node.path("title").textValue())
            .setParameter("content", content).setParameter("pinned", node.path("pinned").booleanValue())
            .setParameter("archived", node.path("archived").booleanValue()).setParameter("color", storedColor(node))
            .setParameter("deleted", optionalInstant(node, "deletedAt")).setParameter("created", created)
            .setParameter("updated", updated).executeUpdate();
        clearTombstone(owner, "NOTE", id);
        tombstoneRemovedChecklist(owner, id, node);
        em.createNativeQuery("delete from checklist_items where owner_id=:owner and note_id=:note")
            .setParameter("owner", owner).setParameter("note", id).executeUpdate();
        if (node.path("checklist").isArray()) for (JsonNode item : node.path("checklist")) {
            UUID itemId = map.checklist(uuid(item, "id"));
            em.createNativeQuery("""
                insert into checklist_items(owner_id,id,note_id,text,checked,sort_order,created_at,updated_at,version)
                values (:owner,:id,:note,:text,:checked,:sort,:created,:updated,0)
                on conflict(owner_id,id) do update set note_id=excluded.note_id,text=excluded.text,
                  checked=excluded.checked,sort_order=excluded.sort_order,updated_at=excluded.updated_at,
                  version=checklist_items.version+1
                """)
                .setParameter("owner", owner).setParameter("id", itemId).setParameter("note", id)
                .setParameter("text", item.path("text").textValue()).setParameter("checked", item.path("checked").booleanValue())
                .setParameter("sort", item.path("sortOrder").intValue()).setParameter("created", created)
                .setParameter("updated", updated).executeUpdate();
            clearTombstone(owner, "CHECKLIST_ITEM", itemId);
        }
        em.createNativeQuery("delete from note_labels where owner_id=:owner and note_id=:note")
            .setParameter("owner", owner).setParameter("note", id).executeUpdate();
        for (JsonNode label : node.path("labels")) em.createNativeQuery("insert into note_labels(owner_id,note_id,label_id) values (:owner,:note,:label)")
            .setParameter("owner", owner).setParameter("note", id).setParameter("label", map.label(UUID.fromString(label.textValue()))).executeUpdate();
        em.createNativeQuery("delete from note_image_references where owner_id=:owner and note_id=:note")
            .setParameter("owner", owner).setParameter("note", id).executeUpdate();
        Set<UUID> references = new LinkedHashSet<>(); for (JsonNode image : node.path("imageIds")) references.add(UUID.fromString(image.textValue()));
        Matcher matcher = IMAGE_REFERENCE.matcher(node.path("content").textValue()); while (matcher.find()) references.add(UUID.fromString(matcher.group(1)));
        int order = 0; for (UUID image : references) {
            UUID mapped = map.image(image);
            em.createNativeQuery("insert into note_image_references(owner_id,note_id,image_id,sort_order) values (:owner,:note,:image,:sort)")
                .setParameter("owner", owner).setParameter("note", id).setParameter("image", mapped).setParameter("sort", order++).executeUpdate();
            em.createNativeQuery("update image_assets set orphaned_at=null where owner_id=:owner and id=:image")
                .setParameter("owner", owner).setParameter("image", mapped).executeUpdate();
        }
    }

    private void tombstoneRemovedChecklist(UUID owner, UUID note, JsonNode source) {
        Set<UUID> incoming = new HashSet<>(); if (source.path("checklist").isArray()) source.path("checklist").forEach(item -> incoming.add(uuid(item, "id")));
        @SuppressWarnings("unchecked") List<Object[]> existing = em.createNativeQuery("select id,version from checklist_items where owner_id=:owner and note_id=:note")
            .setParameter("owner", owner).setParameter("note", note).getResultList();
        Instant now = clock.now(); for (Object[] row : existing) if (!incoming.contains(row[0]))
            em.persist(new TombstoneEntity(ids.nextId(), owner, "CHECKLIST_ITEM", (UUID) row[0], now,
                now.plus(30, ChronoUnit.DAYS), ((Number) row[1]).longValue()));
    }

    private boolean pristine(UUID owner) {
        long notebooks = count("notebooks", owner), notes = count("notes", owner), labels = count("labels", owner), images = count("image_assets", owner);
        return notebooks == 1 && notes == 0 && labels == 0 && images == 0;
    }
    private long count(String table, UUID owner) { return ((Number) em.createNativeQuery("select count(*) from " + table + " where owner_id=:owner").setParameter("owner", owner).getSingleResult()).longValue(); }
    private boolean targetConflicts(UUID owner, PortableTransferCodec.Inspection value) {
        return conflicts("notebooks", owner, value.notebookIds()) || conflicts("notes", owner, value.noteIds())
            || conflicts("labels", owner, value.labelIds()) || conflicts("image_assets", owner, value.imageIds())
            || conflicts("checklist_items", owner, value.checklistItemIds())
            || tombstoneConflicts("NOTEBOOK", owner, value.notebookIds())
            || tombstoneConflicts("NOTE", owner, value.noteIds())
            || tombstoneConflicts("LABEL", owner, value.labelIds())
            || tombstoneConflicts("IMAGE_ASSET", owner, value.imageIds())
            || tombstoneConflicts("CHECKLIST_ITEM", owner, value.checklistItemIds());
    }
    private boolean conflicts(String table, UUID owner, Set<UUID> values) {
        List<UUID> ids = new ArrayList<>(values);
        for (int start = 0; start < ids.size(); start += 500)
            if (!em.createNativeQuery("select id from " + table + " where owner_id=:owner and id in (:ids) limit 1")
                .setParameter("owner", owner).setParameter("ids", ids.subList(start, Math.min(ids.size(), start + 500)))
                .getResultList().isEmpty()) return true;
        return false;
    }
    private boolean tombstoneConflicts(String type, UUID owner, Set<UUID> values) {
        List<UUID> ids = new ArrayList<>(values);
        for (int start = 0; start < ids.size(); start += 500)
            if (!em.createNativeQuery("select id from tombstones where owner_id=:owner and entity_type=:type " +
                    "and entity_id in (:ids) limit 1")
                .setParameter("owner", owner).setParameter("type", type)
                .setParameter("ids", ids.subList(start, Math.min(ids.size(), start + 500)))
                .getResultList().isEmpty()) return true;
        return false;
    }
    private void clearTombstone(UUID owner, String type, UUID id) {
        em.createNativeQuery("delete from tombstones where owner_id=:owner and entity_type=:type and entity_id=:id")
            .setParameter("owner", owner).setParameter("type", type).setParameter("id", id).executeUpdate();
    }
    private UUID uuid(JsonNode node, String field) { return UUID.fromString(node.path(field).textValue()); }
    private Instant instant(JsonNode node, String field) { return Instant.parse(node.path(field).textValue()); }
    private Instant optionalInstant(JsonNode node, String field) { return node.path(field).isTextual() ? Instant.parse(node.path(field).textValue()) : null; }
    private String storedColor(JsonNode node) { return node.path("color").isTextual() ? node.path("color").textValue().toUpperCase(Locale.ROOT) : null; }
    private static final class IdMap {
        private final UUID job; private final boolean copy;
        private final Map<UUID, UUID> notebooks = new java.util.HashMap<>();
        private final Map<UUID, UUID> notes = new java.util.HashMap<>();
        private final Map<UUID, UUID> labels = new java.util.HashMap<>();
        private final Map<UUID, UUID> images = new java.util.HashMap<>();
        private final Map<UUID, UUID> checklist = new java.util.HashMap<>();
        IdMap(UUID job, String strategy) { this.job = job; copy = strategy.equals("ADD_AS_COPIES"); }
        boolean copy() { return copy; }
        UUID notebook(UUID id) { return mapped(notebooks, "notebook", id); }
        UUID note(UUID id) { return mapped(notes, "note", id); }
        UUID label(UUID id) { return mapped(labels, "label", id); }
        UUID image(UUID id) { return mapped(images, "image", id); }
        UUID checklist(UUID id) { return mapped(checklist, "checklist", id); }
        UUID mapped(Map<UUID, UUID> values, String type, UUID id) { return copy ? values.computeIfAbsent(id, value -> UUID.nameUUIDFromBytes((job + ":" + type + ":" + value).getBytes(StandardCharsets.UTF_8))) : id; }
    }
}
