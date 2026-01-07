-- Add blocked column to customers table for PIX payment integration
ALTER TABLE customers ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT false;

-- Add index for faster queries on blocked customers
CREATE INDEX idx_customers_blocked ON customers(blocked);
