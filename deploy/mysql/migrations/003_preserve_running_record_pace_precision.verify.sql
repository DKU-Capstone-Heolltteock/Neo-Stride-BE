-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '003_missing_running_records_pace' AS check_name, COUNT(*) AS failures
FROM (SELECT 1) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND c.table_name = 'running_records'
      AND c.column_name = 'pace'
      AND c.data_type IN ('decimal', 'int')
)
HAVING failures > 0;
