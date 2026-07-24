UPDATE note_versions
SET content_hash = encode(sha256(convert_to(content_payload::text, 'UTF8')), 'hex')
WHERE content_hash IS NULL;

ALTER TABLE note_versions
    ALTER COLUMN content_hash SET NOT NULL;

CREATE OR REPLACE FUNCTION refresh_note_checklist_search() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE'
        OR (TG_OP = 'UPDATE'
            AND (OLD.owner_id, OLD.note_id) IS DISTINCT FROM (NEW.owner_id, NEW.note_id)) THEN
        UPDATE notes n
        SET checklist_search_text = coalesce((
            SELECT string_agg(i.text, ' ' ORDER BY i.sort_order, i.id)
            FROM checklist_items i
            WHERE i.owner_id = OLD.owner_id AND i.note_id = OLD.note_id
        ), '')
        WHERE n.owner_id = OLD.owner_id AND n.id = OLD.note_id;
    END IF;

    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        UPDATE notes n
        SET checklist_search_text = coalesce((
            SELECT string_agg(i.text, ' ' ORDER BY i.sort_order, i.id)
            FROM checklist_items i
            WHERE i.owner_id = NEW.owner_id AND i.note_id = NEW.note_id
        ), '')
        WHERE n.owner_id = NEW.owner_id AND n.id = NEW.note_id;
    END IF;

    RETURN coalesce(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

ALTER TABLE transfer_jobs
    DROP CONSTRAINT ck_transfer_scope;

ALTER TABLE transfer_jobs
    ADD CONSTRAINT ck_transfer_scope CHECK (
        (job_kind = 'IMPORT' AND scope_kind IS NULL AND scope_entity_id IS NULL)
        OR (
            job_kind = 'EXPORT'
            AND (
                (scope_kind = 'ALL' AND scope_entity_id IS NULL)
                OR (scope_kind IN ('NOTEBOOK', 'NOTE') AND scope_entity_id IS NOT NULL)
            )
        )
    );
