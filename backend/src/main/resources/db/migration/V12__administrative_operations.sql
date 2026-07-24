ALTER TABLE instance_settings
    ADD CONSTRAINT ck_settings_image_size
        CHECK (maximum_image_bytes BETWEEN 1048576 AND 52428800),
    ADD CONSTRAINT ck_settings_storage_quota
        CHECK (per_user_storage_quota_bytes BETWEEN 10485760 AND 1099511627776),
    ADD CONSTRAINT ck_settings_admin_deletion_retention
        CHECK (admin_deletion_retention_days BETWEEN 0 AND 3650),
    ADD CONSTRAINT ck_settings_history_count
        CHECK (note_version_maximum_count BETWEEN 1 AND 500),
    ADD CONSTRAINT ck_settings_history_retention
        CHECK (note_version_retention_days BETWEEN 1 AND 3650),
    ADD CONSTRAINT ck_settings_audit_retention
        CHECK (audit_retention_days BETWEEN 1 AND 3650),
    ADD CONSTRAINT ck_settings_log_retention
        CHECK (operational_log_retention_days BETWEEN 1 AND 365);

CREATE TABLE instance_logo (
    singleton_key SMALLINT PRIMARY KEY DEFAULT 1 CHECK (singleton_key = 1),
    content BYTEA NOT NULL,
    content_type VARCHAR(64) NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp')),
    byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 1 AND 2097152),
    checksum CHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE smtp_delivery_status (
    singleton_key SMALLINT PRIMARY KEY DEFAULT 1 CHECK (singleton_key = 1),
    last_successful_at TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ,
    last_failure_category VARCHAR(32)
        CHECK (last_failure_category IN ('CONNECTION', 'AUTHENTICATION', 'TLS', 'DELIVERY', 'UNKNOWN')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO smtp_delivery_status(singleton_key) VALUES (1);

CREATE TABLE scheduled_job_runs (
    job_name VARCHAR(128) PRIMARY KEY,
    run_id UUID,
    state VARCHAR(16) NOT NULL DEFAULT 'IDLE'
        CHECK (state IN ('IDLE', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_category VARCHAR(128),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_audit_type_occurred ON audit_events(event_type, occurred_at DESC, id);
CREATE INDEX ix_audit_result_occurred ON audit_events(result, occurred_at DESC, id);
