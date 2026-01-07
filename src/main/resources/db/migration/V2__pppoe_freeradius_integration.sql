-- PPPoE + FreeRADIUS Integration

-- Create plans table
CREATE TABLE plans (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    upload_mbps INTEGER NOT NULL,
    download_mbps INTEGER NOT NULL,
    monthly_price NUMERIC(12,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    description TEXT,
    CONSTRAINT fk_plan_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- Update customers table to support PPPoE authentication
ALTER TABLE customers ADD COLUMN IF NOT EXISTS email VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS plan_id UUID;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS blocked BOOLEAN NOT NULL DEFAULT false;

-- Add foreign key for plan
ALTER TABLE customers ADD CONSTRAINT fk_customer_plan 
    FOREIGN KEY (plan_id) REFERENCES plans(id);

-- Create unique index on email
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_email ON customers(email);

-- FreeRADIUS radcheck table (user authentication)
CREATE TABLE radcheck (
    id SERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    attribute VARCHAR(64) NOT NULL,
    op VARCHAR(2) NOT NULL DEFAULT '==',
    value VARCHAR(253) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_radcheck_username ON radcheck(username);

-- FreeRADIUS radreply table (user reply attributes)
CREATE TABLE radreply (
    id SERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    attribute VARCHAR(64) NOT NULL,
    op VARCHAR(2) NOT NULL DEFAULT '=',
    value VARCHAR(253) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_radreply_username ON radreply(username);

-- FreeRADIUS radacct table (accounting/session tracking)
CREATE TABLE radacct (
    radacctid BIGSERIAL PRIMARY KEY,
    acctsessionid VARCHAR(64) NOT NULL,
    acctuniqueid VARCHAR(32) NOT NULL UNIQUE,
    username VARCHAR(64),
    realm VARCHAR(64),
    nasipaddress INET NOT NULL,
    nasportid VARCHAR(15),
    nasporttype VARCHAR(32),
    acctstarttime TIMESTAMP WITH TIME ZONE,
    acctupdatetime TIMESTAMP WITH TIME ZONE,
    acctstoptime TIMESTAMP WITH TIME ZONE,
    acctinterval INTEGER,
    acctsessiontime INTEGER,
    acctauthentic VARCHAR(32),
    connectinfo_start VARCHAR(50),
    connectinfo_stop VARCHAR(50),
    acctinputoctets BIGINT,
    acctoutputoctets BIGINT,
    calledstationid VARCHAR(50),
    callingstationid VARCHAR(50),
    acctterminatecause VARCHAR(32),
    servicetype VARCHAR(32),
    framedprotocol VARCHAR(32),
    framedipaddress INET,
    framedipv6address INET,
    framedipv6prefix VARCHAR(45),
    framedinterfaceid VARCHAR(44),
    delegatedipv6prefix VARCHAR(45)
);

-- Indexes for radacct performance
CREATE INDEX idx_radacct_username ON radacct(username);
CREATE INDEX idx_radacct_session ON radacct(acctsessionid);
CREATE INDEX idx_radacct_nasip ON radacct(nasipaddress);
CREATE INDEX idx_radacct_start ON radacct(acctstarttime);
CREATE INDEX idx_radacct_stop ON radacct(acctstoptime);
CREATE INDEX idx_radacct_active ON radacct(acctstoptime) WHERE acctstoptime IS NULL;

-- FreeRADIUS radpostauth table (authentication logging)
CREATE TABLE radpostauth (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    pass VARCHAR(64),
    reply VARCHAR(32),
    authdate TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_radpostauth_username ON radpostauth(username);
CREATE INDEX idx_radpostauth_date ON radpostauth(authdate);

-- Insert sample plans
INSERT INTO plans (id, version, created_at, updated_at, tenant_id, name, upload_mbps, download_mbps, monthly_price, active, description)
SELECT 
    gen_random_uuid(),
    0,
    NOW(),
    NOW(),
    id,
    'BASIC',
    10,
    20,
    49.90,
    true,
    'Plano básico 20Mbps'
FROM tenants
WHERE code = 'default'
LIMIT 1;

INSERT INTO plans (id, version, created_at, updated_at, tenant_id, name, upload_mbps, download_mbps, monthly_price, active, description)
SELECT 
    gen_random_uuid(),
    0,
    NOW(),
    NOW(),
    id,
    'PREMIUM',
    50,
    100,
    99.90,
    true,
    'Plano premium 100Mbps'
FROM tenants
WHERE code = 'default'
LIMIT 1;
