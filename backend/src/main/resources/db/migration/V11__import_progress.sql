-- Real pipeline progress: the frontend shows rows actually processed,
-- not an animation. Updated by PipelineService as the batch runs.
ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS processed_count INT NOT NULL DEFAULT 0;
