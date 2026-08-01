ALTER TABLE endpoint_rate_limits DROP CONSTRAINT endpoint_rate_limits_scope_check;
ALTER TABLE endpoint_rate_limits ADD CONSTRAINT endpoint_rate_limits_scope_check CHECK (
    scope IN (
        'RESET_IDENTIFIER', 'RESET_IP', 'TOKEN_IP', 'INVITATION_ADMIN',
        'EMAIL_CHANGE_USER', 'EMAIL_CHANGE_IP',
        'STEP_UP_USER', 'STEP_UP_IP'
    )
);
