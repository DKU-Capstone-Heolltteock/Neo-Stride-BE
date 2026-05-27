-- Normalize coaching pace columns to the frontend contract: integer seconds per kilometer.
-- Widen first so DECIMAL minute values such as 5.733333 can be multiplied to 344 without overflow.
ALTER TABLE goals MODIFY COLUMN target_pace DECIMAL(10,6) NOT NULL;
ALTER TABLE plans MODIFY COLUMN target_pace DECIMAL(10,6) NOT NULL;

UPDATE goals
SET target_pace = ROUND(target_pace * 60)
WHERE target_pace IS NOT NULL AND target_pace > 0 AND target_pace < 60;

UPDATE plans
SET target_pace = ROUND(target_pace * 60)
WHERE target_pace IS NOT NULL AND target_pace > 0 AND target_pace < 60;

ALTER TABLE goals MODIFY COLUMN target_pace INT NOT NULL;
ALTER TABLE plans MODIFY COLUMN target_pace INT NOT NULL;
