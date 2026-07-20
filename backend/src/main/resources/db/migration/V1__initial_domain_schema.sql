CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    username_normalized VARCHAR(64) NOT NULL,
    email VARCHAR(320) NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    display_name VARCHAR(128),
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    status VARCHAR(32) NOT NULL CHECK (
        status IN ('PENDING_ACTIVATION', 'ACTIVE', 'LOCKED', 'DEACTIVATED', 'PENDING_DELETION', 'DELETED')
    ),
    password_hash VARCHAR(512),
    password_changed_at TIMESTAMPTZ,
    failed_login_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    deactivated_at TIMESTAMPTZ,
    pending_deletion_at TIMESTAMPTZ,
    deletion_due_at TIMESTAMPTZ,
    deletion_initiated_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    last_login_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_users_username_normalized UNIQUE (username_normalized),
    CONSTRAINT uq_users_email_normalized UNIQUE (email_normalized),
    CONSTRAINT ck_users_deletion_window CHECK (
        deletion_due_at IS NULL OR pending_deletion_at IS NOT NULL
    )
);

CREATE INDEX ix_users_status_role ON app_users(status, role);
CREATE INDEX ix_users_deletion_due ON app_users(deletion_due_at)
    WHERE status = 'PENDING_DELETION';

CREATE TABLE user_password_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    password_hash VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_password_history_user_created
    ON user_password_history(user_id, created_at DESC);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    remember_me BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    ip_address INET,
    client_description VARCHAR(512),
    CONSTRAINT ck_session_expiry CHECK (expires_at > created_at)
);
CREATE INDEX ix_sessions_user_active
    ON user_sessions(user_id, expires_at) WHERE revoked_at IS NULL;
CREATE INDEX ix_sessions_expiry ON user_sessions(expires_at) WHERE revoked_at IS NULL;

CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    proposed_username VARCHAR(64) NOT NULL,
    proposed_username_normalized VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    display_name VARCHAR(128),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    created_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_invitation_expiry CHECK (expires_at > created_at)
);
CREATE UNIQUE INDEX uq_invitations_pending_email
    ON invitations(email_normalized) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_invitations_pending_username
    ON invitations(proposed_username_normalized) WHERE status = 'PENDING';
CREATE INDEX ix_invitations_expiry ON invitations(expires_at) WHERE status = 'PENDING';

CREATE TABLE security_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    token_type VARCHAR(32) NOT NULL CHECK (
        token_type IN ('PASSWORD_RESET', 'EMAIL_CHANGE')
    ),
    target_email VARCHAR(320),
    target_email_normalized VARCHAR(320),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_security_token_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_email_change_target CHECK (
        token_type <> 'EMAIL_CHANGE' OR target_email_normalized IS NOT NULL
    )
);
CREATE INDEX ix_security_tokens_user_type
    ON security_tokens(user_id, token_type, expires_at);
CREATE INDEX ix_security_tokens_expiry
    ON security_tokens(expires_at) WHERE consumed_at IS NULL AND revoked_at IS NULL;

CREATE TABLE notebooks (
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL CHECK (length(btrim(name)) > 0),
    color VARCHAR(32),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (owner_id, id)
);
CREATE UNIQUE INDEX uq_notebooks_one_default
    ON notebooks(owner_id) WHERE is_default;
CREATE INDEX ix_notebooks_owner_order ON notebooks(owner_id, sort_order, id);

CREATE TABLE notes (
    owner_id UUID NOT NULL,
    id UUID NOT NULL,
    notebook_id UUID NOT NULL,
    note_type VARCHAR(16) NOT NULL CHECK (note_type IN ('text', 'checklist')),
    title TEXT NOT NULL DEFAULT '',
    content TEXT NOT NULL DEFAULT '',
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    color VARCHAR(32),
    deleted_at TIMESTAMPTZ,
    last_snapshot_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    search_vector TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, ''))
    ) STORED,
    PRIMARY KEY (owner_id, id),
    FOREIGN KEY (owner_id, notebook_id)
        REFERENCES notebooks(owner_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_notes_owner_notebook_updated
    ON notes(owner_id, notebook_id, updated_at DESC, id);
CREATE INDEX ix_notes_owner_collection
    ON notes(owner_id, deleted_at, archived, pinned DESC, updated_at DESC, id);
CREATE INDEX ix_notes_search_vector ON notes USING GIN(search_vector);

CREATE TABLE checklist_items (
    owner_id UUID NOT NULL,
    id UUID NOT NULL,
    note_id UUID NOT NULL,
    text TEXT NOT NULL DEFAULT '',
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (owner_id, id),
    FOREIGN KEY (owner_id, note_id)
        REFERENCES notes(owner_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_checklist_owner_note_order
    ON checklist_items(owner_id, note_id, sort_order, id);

CREATE TABLE labels (
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL CHECK (length(btrim(name)) > 0),
    name_normalized VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (owner_id, id),
    UNIQUE (owner_id, name_normalized)
);
CREATE INDEX ix_labels_owner_name ON labels(owner_id, name_normalized, id);

CREATE TABLE note_labels (
    owner_id UUID NOT NULL,
    note_id UUID NOT NULL,
    label_id UUID NOT NULL,
    PRIMARY KEY (owner_id, note_id, label_id),
    FOREIGN KEY (owner_id, note_id)
        REFERENCES notes(owner_id, id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id, label_id)
        REFERENCES labels(owner_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_note_labels_owner_label ON note_labels(owner_id, label_id, note_id);

CREATE TABLE image_assets (
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    mime_type VARCHAR(64) NOT NULL CHECK (mime_type IN ('image/png', 'image/jpeg', 'image/webp')),
    original_file_name VARCHAR(512),
    byte_size BIGINT NOT NULL CHECK (byte_size >= 0),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    content_hash VARCHAR(128) NOT NULL,
    storage_backend VARCHAR(16) NOT NULL CHECK (storage_backend IN ('FILESYSTEM', 'POSTGRESQL', 'S3')),
    storage_key VARCHAR(1024) NOT NULL,
    orphaned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (owner_id, id)
);
CREATE INDEX ix_images_owner_created ON image_assets(owner_id, created_at, id);
CREATE INDEX ix_images_owner_orphaned ON image_assets(owner_id, orphaned_at)
    WHERE orphaned_at IS NOT NULL;

CREATE TABLE note_image_references (
    owner_id UUID NOT NULL,
    note_id UUID NOT NULL,
    image_id UUID NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (owner_id, note_id, image_id),
    FOREIGN KEY (owner_id, note_id)
        REFERENCES notes(owner_id, id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id, image_id)
        REFERENCES image_assets(owner_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_note_images_owner_image
    ON note_image_references(owner_id, image_id, note_id);

CREATE TABLE note_versions (
    owner_id UUID NOT NULL,
    id UUID NOT NULL,
    note_id UUID NOT NULL,
    source_version BIGINT NOT NULL CHECK (source_version >= 0),
    snapshot_reason VARCHAR(32) NOT NULL CHECK (
        snapshot_reason IN ('EDITOR_CLOSE', 'PERIODIC', 'CONFLICT', 'TRASH_RESTORE', 'VERSION_RESTORE')
    ),
    snapshot_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    content_payload JSONB NOT NULL CHECK (jsonb_typeof(content_payload) = 'object'),
    PRIMARY KEY (owner_id, id),
    FOREIGN KEY (owner_id, note_id)
        REFERENCES notes(owner_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_note_versions_owner_note_snapshot
    ON note_versions(owner_id, note_id, snapshot_at DESC, id);

CREATE TABLE note_version_image_references (
    owner_id UUID NOT NULL,
    note_version_id UUID NOT NULL,
    image_id UUID NOT NULL,
    PRIMARY KEY (owner_id, note_version_id, image_id),
    FOREIGN KEY (owner_id, note_version_id)
        REFERENCES note_versions(owner_id, id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id, image_id)
        REFERENCES image_assets(owner_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_version_images_owner_image
    ON note_version_image_references(owner_id, image_id, note_version_id);

CREATE TABLE tombstones (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    entity_type VARCHAR(32) NOT NULL CHECK (
        entity_type IN ('NOTEBOOK', 'NOTE', 'CHECKLIST_ITEM', 'LABEL', 'IMAGE_ASSET')
    ),
    entity_id UUID NOT NULL,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    last_version BIGINT NOT NULL CHECK (last_version >= 0),
    UNIQUE (owner_id, entity_type, entity_id),
    CONSTRAINT ck_tombstone_expiry CHECK (expires_at > deleted_at)
);
CREATE INDEX ix_tombstones_owner_deleted
    ON tombstones(owner_id, deleted_at, entity_type, entity_id);
CREATE INDEX ix_tombstones_expiry ON tombstones(expires_at);

CREATE TABLE user_settings (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    theme VARCHAR(8) NOT NULL DEFAULT 'dark' CHECK (theme IN ('dark', 'light')),
    language VARCHAR(2) NOT NULL DEFAULT 'en' CHECK (language IN ('en', 'de')),
    move_checked_to_bottom BOOLEAN NOT NULL DEFAULT FALSE,
    trash_auto_purge_days INTEGER CHECK (trash_auto_purge_days IS NULL OR trash_auto_purge_days >= 1),
    last_selected_notebook_id UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    FOREIGN KEY (user_id, last_selected_notebook_id)
        REFERENCES notebooks(owner_id, id) ON DELETE SET NULL (last_selected_notebook_id)
);

CREATE TABLE instance_state (
    singleton_key SMALLINT PRIMARY KEY DEFAULT 1 CHECK (singleton_key = 1),
    initialized BOOLEAN NOT NULL DEFAULT FALSE,
    initialized_at TIMESTAMPTZ,
    initialized_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    schema_metadata_version INTEGER NOT NULL DEFAULT 1 CHECK (schema_metadata_version >= 1),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);
INSERT INTO instance_state(singleton_key) VALUES (1);

CREATE TABLE instance_settings (
    singleton_key SMALLINT PRIMARY KEY DEFAULT 1 CHECK (singleton_key = 1),
    instance_name VARCHAR(128) NOT NULL DEFAULT 'Glacier Notes',
    instance_logo_asset_id UUID,
    default_language VARCHAR(2) NOT NULL DEFAULT 'en' CHECK (default_language IN ('en', 'de')),
    allowed_upload_types TEXT[] NOT NULL DEFAULT ARRAY['image/png', 'image/jpeg', 'image/webp'],
    maximum_image_bytes BIGINT NOT NULL DEFAULT 10485760 CHECK (maximum_image_bytes > 0),
    per_user_storage_quota_bytes BIGINT NOT NULL DEFAULT 1073741824 CHECK (per_user_storage_quota_bytes > 0),
    default_trash_retention_days INTEGER NOT NULL DEFAULT 30 CHECK (default_trash_retention_days > 0),
    users_may_disable_auto_purge BOOLEAN NOT NULL DEFAULT TRUE,
    admin_deletion_retention_days INTEGER NOT NULL DEFAULT 30 CHECK (admin_deletion_retention_days >= 0),
    invitation_expiration_hours INTEGER NOT NULL DEFAULT 168 CHECK (invitation_expiration_hours > 0),
    password_reset_expiration_minutes INTEGER NOT NULL DEFAULT 60 CHECK (password_reset_expiration_minutes > 0),
    email_change_expiration_minutes INTEGER NOT NULL DEFAULT 60 CHECK (email_change_expiration_minutes > 0),
    normal_session_duration_minutes INTEGER NOT NULL DEFAULT 720 CHECK (normal_session_duration_minutes > 0),
    remember_session_duration_minutes INTEGER NOT NULL DEFAULT 43200 CHECK (remember_session_duration_minutes > 0),
    user_exports_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    self_deletion_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    public_base_url VARCHAR(2048),
    smtp_sender_name VARCHAR(128),
    smtp_sender_address VARCHAR(320),
    allowed_email_domains TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    note_version_maximum_count INTEGER NOT NULL DEFAULT 20 CHECK (note_version_maximum_count > 0),
    note_version_retention_days INTEGER NOT NULL DEFAULT 30 CHECK (note_version_retention_days > 0),
    audit_retention_days INTEGER NOT NULL DEFAULT 365 CHECK (audit_retention_days > 0),
    operational_log_retention_days INTEGER NOT NULL DEFAULT 30 CHECK (operational_log_retention_days > 0),
    login_delay_threshold INTEGER NOT NULL DEFAULT 5 CHECK (login_delay_threshold > 0),
    login_lock_threshold INTEGER NOT NULL DEFAULT 10 CHECK (login_lock_threshold >= login_delay_threshold),
    login_lock_minutes INTEGER NOT NULL DEFAULT 15 CHECK (login_lock_minutes > 0),
    common_password_check_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    password_history_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);
INSERT INTO instance_settings(singleton_key) VALUES (1);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    target_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    target_entity_type VARCHAR(64),
    target_entity_id UUID,
    result VARCHAR(16) NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED')),
    ip_address INET,
    client_description VARCHAR(512),
    correlation_id VARCHAR(128) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB CHECK (jsonb_typeof(metadata_json) = 'object')
);
CREATE INDEX ix_audit_occurred ON audit_events(occurred_at DESC, id);
CREATE INDEX ix_audit_actor_occurred ON audit_events(actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_target_occurred ON audit_events(target_user_id, occurred_at DESC);

CREATE TABLE backup_jobs (
    id UUID PRIMARY KEY,
    created_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    output_identifier VARCHAR(1024),
    byte_size BIGINT CHECK (byte_size IS NULL OR byte_size >= 0),
    checksum VARCHAR(128),
    error_code VARCHAR(64),
    error_message VARCHAR(512),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE INDEX ix_backup_jobs_state_created ON backup_jobs(state, created_at, id);

CREATE TABLE job_locks (
    job_name VARCHAR(128) PRIMARY KEY,
    locked_by VARCHAR(128),
    locked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);
