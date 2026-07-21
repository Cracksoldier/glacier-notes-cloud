ALTER TABLE invitations
    ALTER COLUMN proposed_username DROP NOT NULL,
    ALTER COLUMN proposed_username_normalized DROP NOT NULL;

CREATE TABLE endpoint_rate_limits (
    scope VARCHAR(32) NOT NULL CHECK (
        scope IN ('RESET_IDENTIFIER', 'RESET_IP', 'TOKEN_IP', 'INVITATION_ADMIN')
    ),
    key_hash CHAR(64) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL CHECK (attempt_count >= 0),
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (scope, key_hash)
);

CREATE INDEX ix_endpoint_rate_limits_blocked
    ON endpoint_rate_limits(blocked_until)
    WHERE blocked_until IS NOT NULL;

ALTER TABLE instance_settings
    ADD CONSTRAINT ck_settings_invitation_expiration
        CHECK (invitation_expiration_hours BETWEEN 1 AND 720),
    ADD CONSTRAINT ck_settings_password_reset_expiration
        CHECK (password_reset_expiration_minutes BETWEEN 5 AND 1440);
