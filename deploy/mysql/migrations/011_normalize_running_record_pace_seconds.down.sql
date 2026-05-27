-- Revert only the column type. Values remain seconds/km because the original minute decimals cannot be reconstructed.
ALTER TABLE running_records MODIFY COLUMN pace DECIMAL(8,2) NULL;
