ALTER TABLE notebooks
    ADD CONSTRAINT ck_notebooks_color CHECK (
        color IS NULL OR color IN ('RED', 'ORANGE', 'YELLOW', 'GREEN', 'BLUE', 'PURPLE', 'GRAY')
    ),
    ADD CONSTRAINT ck_notebooks_sort_order CHECK (sort_order >= 0);

ALTER TABLE notes
    ADD CONSTRAINT ck_notes_color CHECK (
        color IS NULL OR color IN ('RED', 'ORANGE', 'YELLOW', 'GREEN', 'BLUE', 'PURPLE', 'GRAY')
    );

ALTER TABLE checklist_items
    ADD CONSTRAINT ck_checklist_sort_order CHECK (sort_order >= 0);

CREATE INDEX ix_notes_owner_type_collection
    ON notes(owner_id, note_type, deleted_at, archived, pinned DESC, updated_at DESC, id);
