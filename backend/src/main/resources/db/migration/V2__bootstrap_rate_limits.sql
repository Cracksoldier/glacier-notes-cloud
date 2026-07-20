CREATE TABLE bootstrap_rate_limits (
    client_key CHAR(64) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    failure_count INTEGER NOT NULL CHECK (failure_count >= 0),
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_bootstrap_rate_limits_blocked
    ON bootstrap_rate_limits(blocked_until)
    WHERE blocked_until IS NOT NULL;
