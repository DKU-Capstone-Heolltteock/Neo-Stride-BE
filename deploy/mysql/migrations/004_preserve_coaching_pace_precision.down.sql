-- Revert coaching pace columns to integer minute pace.
-- WARNING: this drops decimal precision for existing target_pace values.
ALTER TABLE goals MODIFY COLUMN target_pace INT NULL;
ALTER TABLE plans MODIFY COLUMN target_pace INT NULL;
