ALTER TABLE endpoint_rate_limits DROP CONSTRAINT endpoint_rate_limits_scope_check;
ALTER TABLE endpoint_rate_limits ADD CONSTRAINT endpoint_rate_limits_scope_check CHECK (
    scope IN (
        'RESET_IDENTIFIER', 'RESET_IP', 'TOKEN_IP', 'INVITATION_ADMIN',
        'EMAIL_CHANGE_USER', 'EMAIL_CHANGE_IP'
    )
);

CREATE UNIQUE INDEX uq_security_tokens_pending_email_change_user
    ON security_tokens(user_id)
    WHERE token_type = 'EMAIL_CHANGE' AND consumed_at IS NULL AND revoked_at IS NULL;

CREATE UNIQUE INDEX uq_security_tokens_pending_email_change_target
    ON security_tokens(target_email_normalized)
    WHERE token_type = 'EMAIL_CHANGE' AND consumed_at IS NULL AND revoked_at IS NULL;

UPDATE user_settings
SET trash_auto_purge_days = (
    SELECT default_trash_retention_days FROM instance_settings WHERE singleton_key = 1
)
WHERE trash_auto_purge_days IS NULL;

ALTER TABLE user_settings DROP CONSTRAINT user_settings_trash_auto_purge_days_check;
ALTER TABLE user_settings ADD CONSTRAINT user_settings_trash_auto_purge_days_check CHECK (
    trash_auto_purge_days IS NULL OR trash_auto_purge_days BETWEEN 1 AND 3650
);

ALTER TABLE instance_settings ADD CONSTRAINT ck_settings_email_change_expiration
    CHECK (email_change_expiration_minutes BETWEEN 5 AND 1440);
ALTER TABLE instance_settings ADD CONSTRAINT ck_settings_trash_retention
    CHECK (default_trash_retention_days BETWEEN 1 AND 3650);
ALTER TABLE instance_settings ADD CONSTRAINT ck_settings_deletion_retention
    CHECK (admin_deletion_retention_days BETWEEN 0 AND 3650);
