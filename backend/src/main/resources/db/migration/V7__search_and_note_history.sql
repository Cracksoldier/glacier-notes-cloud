DROP INDEX ix_notes_search_vector;

ALTER TABLE notes
    DROP COLUMN search_vector,
    ADD COLUMN checklist_search_text TEXT NOT NULL DEFAULT '';

UPDATE notes n
SET checklist_search_text = coalesce((
    SELECT string_agg(i.text, ' ' ORDER BY i.sort_order, i.id)
    FROM checklist_items i
    WHERE i.owner_id = n.owner_id AND i.note_id = n.id
), '');

ALTER TABLE notes
    ADD COLUMN search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(content, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(checklist_search_text, '')), 'B')
    ) STORED;

CREATE INDEX ix_notes_search_vector ON notes USING GIN(search_vector);

CREATE FUNCTION refresh_note_checklist_search() RETURNS TRIGGER AS $$
DECLARE
    affected_owner UUID := coalesce(NEW.owner_id, OLD.owner_id);
    affected_note UUID := coalesce(NEW.note_id, OLD.note_id);
BEGIN
    UPDATE notes n
    SET checklist_search_text = coalesce((
        SELECT string_agg(i.text, ' ' ORDER BY i.sort_order, i.id)
        FROM checklist_items i
        WHERE i.owner_id = affected_owner AND i.note_id = affected_note
    ), '')
    WHERE n.owner_id = affected_owner AND n.id = affected_note;
    RETURN coalesce(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_checklist_search_insert
AFTER INSERT ON checklist_items
FOR EACH ROW EXECUTE FUNCTION refresh_note_checklist_search();

CREATE TRIGGER trg_checklist_search_update
AFTER UPDATE OF text, sort_order, note_id ON checklist_items
FOR EACH ROW EXECUTE FUNCTION refresh_note_checklist_search();

CREATE TRIGGER trg_checklist_search_delete
AFTER DELETE ON checklist_items
FOR EACH ROW EXECUTE FUNCTION refresh_note_checklist_search();

ALTER TABLE note_versions
    ADD COLUMN content_hash VARCHAR(64);

CREATE INDEX ix_note_versions_owner_note_hash
    ON note_versions(owner_id, note_id, snapshot_at DESC, content_hash);
