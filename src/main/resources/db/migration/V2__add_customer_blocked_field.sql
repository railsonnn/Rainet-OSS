-- Add blocked field to customers table for automatic blocking on delinquency
ALTER TABLE customers ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT false;

-- Add index on document field for efficient customer lookup during RADIUS authentication
-- This improves performance when searching customers by document number
CREATE INDEX IF NOT EXISTS idx_customers_document ON customers(document);
