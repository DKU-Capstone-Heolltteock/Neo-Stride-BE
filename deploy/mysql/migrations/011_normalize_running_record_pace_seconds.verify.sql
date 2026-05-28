-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '011_running_records_pace_not_int' AS check_name, COUNT(*) AS failures
FROM information_schema.columns c
WHERE c.table_schema = DATABASE()
  AND c.table_name = 'running_records'
  AND c.column_name = 'pace'
  AND c.data_type <> 'int'
HAVING failures > 0;
