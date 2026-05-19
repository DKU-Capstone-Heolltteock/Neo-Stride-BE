-- Preserve coaching pace decimals instead of rounding/storing whole minutes.
ALTER TABLE goals MODIFY COLUMN target_pace DECIMAL(5,2) NULL;
ALTER TABLE plans MODIFY COLUMN target_pace DECIMAL(5,2) NULL;
