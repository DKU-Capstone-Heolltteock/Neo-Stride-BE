-- Revert coaching pace columns to decimal minutes per kilometer.
ALTER TABLE goals MODIFY COLUMN target_pace DECIMAL(8,6) NOT NULL;
ALTER TABLE plans MODIFY COLUMN target_pace DECIMAL(8,6) NOT NULL;

UPDATE goals
SET target_pace = ROUND(target_pace / 60, 6)
WHERE target_pace IS NOT NULL AND target_pace >= 60;

UPDATE plans
SET target_pace = ROUND(target_pace / 60, 6)
WHERE target_pace IS NOT NULL AND target_pace >= 60;
