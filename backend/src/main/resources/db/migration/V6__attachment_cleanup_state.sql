ALTER TABLE attachment
    ADD COLUMN storage_deleted_at TIMESTAMPTZ,
    ADD COLUMN storage_delete_attempts INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_attachment_pending_storage_delete
    ON attachment (deleted_at, id)
    WHERE deleted = TRUE AND storage_deleted_at IS NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON attachment TO crm_app;
