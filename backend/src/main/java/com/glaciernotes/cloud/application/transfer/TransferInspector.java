package com.glaciernotes.cloud.application.transfer;

import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class TransferInspector {
    private final PortableTransferCodec codec;
    private final EntityManager em;
    private final GlacierConfiguration configuration;

    public TransferInspector(PortableTransferCodec codec, EntityManager em, GlacierConfiguration configuration) {
        this.codec = codec; this.em = em; this.configuration = configuration;
    }

    @Transactional
    public Result inspect(Path path, UUID owner, PortableTransferCodec.Cancellation cancellation) throws IOException {
        var inspection = codec.inspect(path, cancellation);
        var errors = new ArrayList<>(inspection.errors());
        long used = ((Number) em.createNativeQuery("select coalesce(sum(byte_size + coalesce(thumbnail_byte_size,0)),0) from image_assets where owner_id=:owner")
            .setParameter("owner", owner).getSingleResult()).longValue();
        long quota = ((Number) em.createNativeQuery("select per_user_storage_quota_bytes from instance_settings where singleton_key=1")
            .getSingleResult()).longValue();
        if (used + inspection.decodedImageBytes() > quota) errors.add("images exceed the target user's remaining storage quota");
        boolean conflicts = exists("notebooks", inspection.notebookIds(), owner)
            || exists("notes", inspection.noteIds(), owner)
            || exists("labels", inspection.labelIds(), owner)
            || exists("image_assets", inspection.imageIds(), owner)
            || exists("checklist_items", inspection.checklistItemIds(), owner)
            || tombstones("NOTEBOOK", inspection.notebookIds(), owner)
            || tombstones("NOTE", inspection.noteIds(), owner)
            || tombstones("LABEL", inspection.labelIds(), owner)
            || tombstones("IMAGE_ASSET", inspection.imageIds(), owner)
            || tombstones("CHECKLIST_ITEM", inspection.checklistItemIds(), owner);
        return new Result(inspection, conflicts, List.copyOf(errors.stream().limit(100).toList()));
    }

    private boolean exists(String table, Set<UUID> ids, UUID owner) {
        if (ids.isEmpty()) return false;
        List<UUID> values = new ArrayList<>(ids);
        for (int start = 0; start < values.size(); start += 500) {
            List<UUID> chunk = values.subList(start, Math.min(values.size(), start + 500));
            @SuppressWarnings("unchecked") List<UUID> found = em.createNativeQuery(
                    "select id from " + table + " where owner_id=:owner and id in (:ids) limit 1")
                .setParameter("owner", owner).setParameter("ids", chunk).getResultList();
            if (!found.isEmpty()) return true;
        }
        return false;
    }

    private boolean tombstones(String type, Set<UUID> ids, UUID owner) {
        if (ids.isEmpty()) return false;
        List<UUID> values = new ArrayList<>(ids);
        for (int start = 0; start < values.size(); start += 500) {
            List<UUID> chunk = values.subList(start, Math.min(values.size(), start + 500));
            if (!em.createNativeQuery("select id from tombstones where owner_id=:owner " +
                    "and entity_type=:type and entity_id in (:ids) limit 1")
                .setParameter("owner", owner).setParameter("type", type).setParameter("ids", chunk)
                .getResultList().isEmpty()) return true;
        }
        return false;
    }

    public record Result(PortableTransferCodec.Inspection inspection, boolean conflicts, List<String> errors) {}
}
