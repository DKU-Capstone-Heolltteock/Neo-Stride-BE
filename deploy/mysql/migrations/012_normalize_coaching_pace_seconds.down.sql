-- Revert coaching pace columns to decimal minutes per kilometer.
-- Widen first so INT second values such as 344 can be divided without overflow.
ALTER TABLE goals MODIFY COLUMN target_pace DECIMAL(10,6) NOT NULL;
ALTER TABLE plans MODIFY COLUMN target_pace DECIMAL(10,6) NOT NULL;

UPDATE goals
SET target_pace = ROUND(target_pace / 60, 6)
WHERE target_pace IS NOT NULL AND target_pace >= 60;

UPDATE plans
SET target_pace = ROUND(target_pace / 60, 6)
WHERE target_pace IS NOT NULL AND target_pace >= 60;

ALTER TABLE goals MODIFY COLUMN target_pace DECIMAL(8,6) NOT NULL;
ALTER TABLE plans MODIFY COLUMN target_pace DECIMAL(8,6) NOT NULL;
