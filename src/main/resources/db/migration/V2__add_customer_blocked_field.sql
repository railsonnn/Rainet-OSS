-- Add blocked field to customers table for automatic blocking on delinquency
ALTER TABLE customers ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT false;
