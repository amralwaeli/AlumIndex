-- Resumable imports: remember the buffered upload and where to restart so an import
-- interrupted by a restart/crash can continue instead of starting over.
ALTER TABLE import_batches ADD COLUMN storage_key TEXT;
ALTER TABLE import_batches ADD COLUMN next_offset INTEGER NOT NULL DEFAULT 0;
