CREATE TABLE external_storage_operations (
    id UUID PRIMARY KEY,
    operation_kind VARCHAR(32) NOT NULL
        CHECK (operation_kind IN ('ROLLBACK_BINARY_CREATE', 'DELETE_BINARY', 'DELETE_TRANSFER_FILE')),
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'FAILED')),
    owner_id UUID NOT NULL,
    transfer_job_id UUID,
    storage_backend VARCHAR(16)
        CHECK (storage_backend IS NULL OR storage_backend IN ('FILESYSTEM', 'POSTGRESQL', 'S3')),
    primary_location VARCHAR(2048) NOT NULL,
    secondary_location VARCHAR(2048),
    reserved_bytes BIGINT NOT NULL DEFAULT 0 CHECK (reserved_bytes >= 0),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (
        (operation_kind IN ('ROLLBACK_BINARY_CREATE', 'DELETE_BINARY') AND storage_backend IS NOT NULL)
        OR
        (operation_kind = 'DELETE_TRANSFER_FILE' AND storage_backend IS NULL AND secondary_location IS NULL)
    )
);

CREATE INDEX ix_external_storage_operations_claim
    ON external_storage_operations(available_at, created_at)
    WHERE state = 'PENDING';

CREATE INDEX ix_external_storage_operations_owner_reservations
    ON external_storage_operations(owner_id)
    WHERE state = 'PENDING' AND operation_kind = 'ROLLBACK_BINARY_CREATE';

CREATE INDEX ix_external_storage_operations_transfer_job
    ON external_storage_operations(transfer_job_id)
    WHERE transfer_job_id IS NOT NULL;
