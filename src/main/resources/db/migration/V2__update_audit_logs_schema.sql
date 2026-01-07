-- Update audit_logs table to match AuditLog entity
-- Add missing columns for comprehensive audit logging

-- Drop old columns that don't match the entity
ALTER TABLE audit_logs DROP COLUMN IF EXISTS resource;

-- Add new columns
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS resource_type VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS resource_id VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS user_agent TEXT;

-- Remove defaults after backfilling
ALTER TABLE audit_logs ALTER COLUMN resource_type DROP DEFAULT;
ALTER TABLE audit_logs ALTER COLUMN resource_id DROP DEFAULT;
ALTER TABLE audit_logs ALTER COLUMN status DROP DEFAULT;

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_audit_tenant_action ON audit_logs(tenant_id, action);
CREATE INDEX IF NOT EXISTS idx_audit_actor_created ON audit_logs(actor, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_resource_type ON audit_logs(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_status ON audit_logs(status);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_logs(created_at DESC);
