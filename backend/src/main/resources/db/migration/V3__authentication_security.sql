CREATE TABLE login_rate_limits (
    scope VARCHAR(16) NOT NULL CHECK (scope IN ('IDENTIFIER', 'IP')),
    key_hash CHAR(64) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    failure_count INTEGER NOT NULL CHECK (failure_count >= 0),
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (scope, key_hash)
);

CREATE INDEX ix_login_rate_limits_blocked
    ON login_rate_limits(blocked_until)
    WHERE blocked_until IS NOT NULL;

ALTER TABLE instance_settings
    ADD CONSTRAINT ck_settings_normal_session_duration
        CHECK (normal_session_duration_minutes BETWEEN 15 AND 10080),
    ADD CONSTRAINT ck_settings_remember_session_duration
        CHECK (
            remember_session_duration_minutes BETWEEN 1440 AND 525600
            AND remember_session_duration_minutes >= normal_session_duration_minutes
        ),
    ADD CONSTRAINT ck_settings_login_delay_threshold
        CHECK (login_delay_threshold BETWEEN 3 AND 20),
    ADD CONSTRAINT ck_settings_login_lock_threshold
        CHECK (
            login_lock_threshold > login_delay_threshold
            AND login_lock_threshold <= 50
        ),
    ADD CONSTRAINT ck_settings_login_lock_minutes
        CHECK (login_lock_minutes BETWEEN 1 AND 1440);
