-- Preserve frontend second-derived pace decimals such as 5:44/km -> 5.733333 min/km.
ALTER TABLE goals MODIFY COLUMN target_pace DECIMAL(8,6) NOT NULL;
ALTER TABLE plans MODIFY COLUMN target_pace DECIMAL(8,6) NOT NULL;
