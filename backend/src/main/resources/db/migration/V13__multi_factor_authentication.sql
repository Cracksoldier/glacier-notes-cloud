CREATE TABLE user_mfa_totp (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE')),
    secret_ciphertext BYTEA NOT NULL,
    secret_nonce BYTEA NOT NULL,
    key_id VARCHAR(64) NOT NULL,
    algorithm VARCHAR(16) NOT NULL DEFAULT 'SHA1' CHECK (algorithm IN ('SHA1')),
    digits INTEGER NOT NULL DEFAULT 6 CHECK (digits BETWEEN 6 AND 8),
    period_seconds INTEGER NOT NULL DEFAULT 30 CHECK (period_seconds BETWEEN 15 AND 120),
    last_accepted_step BIGINT CHECK (last_accepted_step >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_user_mfa_totp_confirmed CHECK (
        (status = 'PENDING' AND confirmed_at IS NULL)
        OR (status = 'ACTIVE' AND confirmed_at IS NOT NULL)
    )
);

CREATE INDEX ix_user_mfa_totp_pending
    ON user_mfa_totp(created_at)
    WHERE status = 'PENDING';

CREATE TABLE user_mfa_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    code_hash VARCHAR(128) NOT NULL UNIQUE,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMPTZ
);

CREATE INDEX ix_user_mfa_recovery_codes_user ON user_mfa_recovery_codes(user_id);

CREATE TABLE mfa_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    remember_me BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    ip_address INET,
    client_description VARCHAR(512),
    CONSTRAINT ck_mfa_challenge_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_mfa_challenges_user_created
    ON mfa_challenges(user_id, created_at)
    WHERE consumed_at IS NULL;
CREATE INDEX ix_mfa_challenges_expiry
    ON mfa_challenges(expires_at)
    WHERE consumed_at IS NULL;

-- Widening, not narrowing: the replacement admits a strict superset of the original
-- ('IDENTIFIER', 'IP'), every existing row satisfies it, and the (scope, key_hash) primary key is
-- untouched. The original constraint was declared inline and unnamed in V3, so PostgreSQL named it
-- login_rate_limits_scope_check; it can only be widened by dropping and re-adding.
ALTER TABLE login_rate_limits
    DROP CONSTRAINT login_rate_limits_scope_check;
ALTER TABLE login_rate_limits
    ADD CONSTRAINT ck_login_rate_limits_scope
        CHECK (scope IN ('IDENTIFIER', 'IP', 'MFA_IP'));

ALTER TABLE user_sessions
    ADD COLUMN second_factor_verified_at TIMESTAMPTZ;

ALTER TABLE instance_settings
    ADD COLUMN mfa_challenge_lifetime_minutes INTEGER NOT NULL DEFAULT 5
        CHECK (mfa_challenge_lifetime_minutes BETWEEN 1 AND 30),
    ADD COLUMN mfa_challenge_attempt_limit INTEGER NOT NULL DEFAULT 5
        CHECK (mfa_challenge_attempt_limit BETWEEN 3 AND 10),
    ADD COLUMN mfa_pending_enrollment_minutes INTEGER NOT NULL DEFAULT 30
        CHECK (mfa_pending_enrollment_minutes BETWEEN 5 AND 120),
    ADD COLUMN mfa_step_up_grace_minutes INTEGER NOT NULL DEFAULT 5
        CHECK (mfa_step_up_grace_minutes BETWEEN 0 AND 60);
