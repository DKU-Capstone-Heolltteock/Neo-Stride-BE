-- Normalize running record pace to the Android contract: integer seconds per kilometer.
-- Legacy values below 60 are interpreted as minutes/km decimals and converted to seconds/km.
UPDATE running_records
SET pace = ROUND(pace * 60)
WHERE pace IS NOT NULL AND pace > 0 AND pace < 60;

ALTER TABLE running_records MODIFY COLUMN pace INT NULL;
