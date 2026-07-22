ALTER TABLE labels DROP CONSTRAINT IF EXISTS labels_owner_id_name_normalized_key;

ALTER TABLE notebooks DROP CONSTRAINT IF EXISTS ck_notebooks_color;
ALTER TABLE notebooks ADD CONSTRAINT ck_notebooks_color CHECK (
    color IS NULL OR color IN ('RED', 'ORANGE', 'YELLOW', 'GREEN', 'TEAL', 'BLUE', 'PURPLE', 'PINK', 'GRAY')
);
ALTER TABLE notes DROP CONSTRAINT IF EXISTS ck_notes_color;
ALTER TABLE notes ADD CONSTRAINT ck_notes_color CHECK (
    color IS NULL OR color IN ('RED', 'ORANGE', 'YELLOW', 'GREEN', 'TEAL', 'BLUE', 'PURPLE', 'PINK', 'GRAY')
);

CREATE TABLE transfer_jobs (
    id UUID PRIMARY KEY,
    job_kind VARCHAR(8) NOT NULL CHECK (job_kind IN ('EXPORT', 'IMPORT')),
    phase VARCHAR(8) NOT NULL CHECK (phase IN ('GENERATE', 'INSPECT', 'APPLY')),
    state VARCHAR(16) NOT NULL CHECK (
        state IN ('QUEUED', 'RUNNING', 'READY', 'SUCCEEDED', 'FAILED', 'CANCELED', 'EXPIRED')
    ),
    requested_by UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    target_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    blind_admin BOOLEAN NOT NULL DEFAULT FALSE,
    scope_kind VARCHAR(16) CHECK (scope_kind IN ('ALL', 'NOTEBOOK', 'NOTE')),
    scope_entity_id UUID,
    import_strategy VARCHAR(24) CHECK (
        import_strategy IN ('PRESERVE', 'ADD_AS_COPIES', 'REPLACE_BY_ID')
    ),
    temporary_path VARCHAR(2048),
    original_file_name VARCHAR(512),
    byte_size BIGINT CHECK (byte_size IS NULL OR byte_size >= 0),
    counts_json JSONB,
    has_conflicts BOOLEAN,
    quota_impact_bytes BIGINT CHECK (quota_impact_bytes IS NULL OR quota_impact_bytes >= 0),
    errors_json JSONB NOT NULL DEFAULT '[]'::JSONB CHECK (jsonb_typeof(errors_json) = 'array'),
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_transfer_scope CHECK (
        (job_kind = 'IMPORT' AND scope_kind IS NULL AND scope_entity_id IS NULL)
        OR (job_kind = 'EXPORT' AND scope_kind IS NOT NULL)
    )
);

CREATE INDEX ix_transfer_jobs_queue ON transfer_jobs(state, created_at, id);
CREATE INDEX ix_transfer_jobs_requester ON transfer_jobs(requested_by, created_at DESC, id);
CREATE INDEX ix_transfer_jobs_target ON transfer_jobs(target_user_id, created_at DESC, id);
CREATE INDEX ix_transfer_jobs_expiry ON transfer_jobs(expires_at);
