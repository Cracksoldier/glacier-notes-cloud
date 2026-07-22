ALTER TABLE image_assets
    ADD COLUMN thumbnail_mime_type VARCHAR(64),
    ADD COLUMN thumbnail_byte_size BIGINT,
    ADD COLUMN thumbnail_width INTEGER,
    ADD COLUMN thumbnail_height INTEGER,
    ADD COLUMN thumbnail_storage_key VARCHAR(1024);

ALTER TABLE image_assets
    ADD CONSTRAINT ck_image_thumbnail_mime CHECK (thumbnail_mime_type IN ('image/png', 'image/jpeg')),
    ADD CONSTRAINT ck_image_thumbnail_bytes CHECK (thumbnail_byte_size >= 0),
    ADD CONSTRAINT ck_image_thumbnail_width CHECK (thumbnail_width > 0),
    ADD CONSTRAINT ck_image_thumbnail_height CHECK (thumbnail_height > 0);

ALTER TABLE instance_state
    ADD COLUMN image_storage_backend VARCHAR(16)
        CHECK (image_storage_backend IN ('FILESYSTEM', 'POSTGRESQL', 'S3'));

ALTER TABLE instance_settings
    ADD COLUMN image_orphan_grace_hours INTEGER NOT NULL DEFAULT 24
        CHECK (image_orphan_grace_hours BETWEEN 1 AND 720);

CREATE TABLE image_asset_blobs (
    storage_key VARCHAR(1024) PRIMARY KEY,
    content BYTEA NOT NULL,
    content_length BIGINT NOT NULL CHECK (content_length >= 0),
    content_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

