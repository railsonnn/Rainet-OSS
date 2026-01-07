-- Add snapshot_type and config_hash columns to config_snapshots table
ALTER TABLE config_snapshots
ADD COLUMN snapshot_type VARCHAR(50) NOT NULL DEFAULT 'AFTER',
ADD COLUMN config_hash VARCHAR(64) NOT NULL DEFAULT '';

-- Create index for snapshot_type queries
CREATE INDEX idx_config_snapshots_router_type ON config_snapshots(router_id, snapshot_type, created_at DESC);

-- Add columns to audit_logs table to match AuditLog entity
ALTER TABLE audit_logs
ADD COLUMN resource_type VARCHAR(255),
ADD COLUMN resource_id VARCHAR(255),
ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
ADD COLUMN error_message TEXT,
ADD COLUMN ip_address VARCHAR(255),
ADD COLUMN user_agent TEXT;

-- Migrate existing audit_logs data (use resource column before dropping it)
UPDATE audit_logs 
SET resource_type = 'UNKNOWN', 
    resource_id = COALESCE(resource, 'unknown')
WHERE resource_type IS NULL;

-- Drop old resource column after migration
ALTER TABLE audit_logs DROP COLUMN resource;

-- Create indexes for audit_logs
CREATE INDEX idx_audit_logs_tenant_action ON audit_logs(tenant_id, action);
CREATE INDEX idx_audit_logs_actor_created ON audit_logs(actor, created_at);
CREATE INDEX idx_audit_logs_resource_type ON audit_logs(resource_type, resource_id);
