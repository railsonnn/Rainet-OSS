-- Add snapshot_type and config_hash columns to config_snapshots table
-- Note: This migration uses placeholder defaults for existing records.
-- Any existing snapshots without these fields are marked as legacy.

ALTER TABLE config_snapshots 
ADD COLUMN snapshot_type VARCHAR(10) NOT NULL DEFAULT 'AFTER',
ADD COLUMN config_hash VARCHAR(64) NOT NULL DEFAULT 'LEGACY_SNAPSHOT_NO_HASH';

-- Update existing records to mark them as legacy if needed
-- (In production, you might want to recalculate hashes for existing snapshots)
COMMENT ON COLUMN config_snapshots.config_hash IS 'SHA-256 hash of config_script. LEGACY_SNAPSHOT_NO_HASH indicates pre-migration snapshots.';

-- Remove default values after adding columns (to enforce proper values in future inserts)
ALTER TABLE config_snapshots 
ALTER COLUMN snapshot_type DROP DEFAULT,
ALTER COLUMN config_hash DROP DEFAULT;

-- Create index for faster queries by snapshot_type
CREATE INDEX idx_config_snapshots_type ON config_snapshots(snapshot_type);
CREATE INDEX idx_config_snapshots_router_type ON config_snapshots(router_id, snapshot_type);
