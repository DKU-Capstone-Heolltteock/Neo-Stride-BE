-- Revert coaching pace precision to the previous two-decimal minute pace.
-- WARNING: this drops sub-second-derived decimal precision for existing target_pace values.
ALTER TABLE goals MODIFY COLUMN target_pace DECIMAL(5,2) NOT NULL;
ALTER TABLE plans MODIFY COLUMN target_pace DECIMAL(5,2) NOT NULL;
