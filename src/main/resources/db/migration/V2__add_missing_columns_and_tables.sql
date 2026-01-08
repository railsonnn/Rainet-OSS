-- V2__add_missing_columns_and_tables.sql
-- Migration to fix schema inconsistencies identified in audit

-- 1. Add missing columns to customers table
ALTER TABLE customers ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS blocked BOOLEAN DEFAULT FALSE;

-- Create index for email lookups (used by RADIUS authentication)
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers(email);

-- 2. Create missing plans table
CREATE TABLE IF NOT EXISTS plans (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    download_mbps INTEGER NOT NULL,
    upload_mbps INTEGER NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_plan_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- Indexes for plans table
CREATE INDEX IF NOT EXISTS idx_plans_tenant_active ON plans(tenant_id, active);

-- 3. Add missing columns to audit_logs table
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS ip_address VARCHAR(50);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS user_agent TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS resource_type VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS resource_id VARCHAR(255);

-- Update existing audit_logs to set resource_type and resource_id from resource column
UPDATE audit_logs SET resource_type = 'Unknown', resource_id = 'Unknown' WHERE resource_type IS NULL;

-- Make resource_type and resource_id NOT NULL after setting defaults
ALTER TABLE audit_logs ALTER COLUMN resource_type SET NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN resource_id SET NOT NULL;

-- Drop old resource column if it exists (replaced by resource_type + resource_id)
-- Commented out for safety - uncomment after data migration if needed
-- ALTER TABLE audit_logs DROP COLUMN IF EXISTS resource;

-- 4. Add performance indexes
CREATE INDEX IF NOT EXISTS idx_users_username_tenant ON users(username, tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_customers_document_tenant ON customers(document, tenant_id);
CREATE INDEX IF NOT EXISTS idx_customers_status_tenant ON customers(status, tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status_tenant ON invoices(status, tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoices_due_date ON invoices(due_date);
CREATE INDEX IF NOT EXISTS idx_invoices_customer ON invoices(customer_id);
CREATE INDEX IF NOT EXISTS idx_config_snapshots_router_created ON config_snapshots(router_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_routers_pop ON routers(pop_id);
CREATE INDEX IF NOT EXISTS idx_routers_tenant ON routers(tenant_id);

-- 5. Add audit log indexes from AuditLog.java entity definition
CREATE INDEX IF NOT EXISTS idx_audit_tenant_action ON audit_logs(tenant_id, action);
CREATE INDEX IF NOT EXISTS idx_audit_actor_created ON audit_logs(actor, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_resource_type ON audit_logs(resource_type, resource_id);

-- 6. Comments for documentation
COMMENT ON COLUMN customers.email IS 'Customer email used for PPPoE authentication and notifications';
COMMENT ON COLUMN customers.password_hash IS 'BCrypt hashed password for PPPoE authentication';
COMMENT ON COLUMN customers.blocked IS 'True if customer is blocked due to non-payment or administrative action';
COMMENT ON TABLE plans IS 'Internet service plans with bandwidth and pricing configuration';
COMMENT ON COLUMN audit_logs.status IS 'Status of audited operation: SUCCESS, FAILURE, or PARTIAL';
COMMENT ON COLUMN audit_logs.ip_address IS 'IP address of the client that initiated the operation';
COMMENT ON COLUMN audit_logs.user_agent IS 'User agent string from HTTP request header';
