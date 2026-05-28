-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '012_coaching_target_pace_not_int' AS check_name, COUNT(*) AS failures
FROM information_schema.columns c
WHERE c.table_schema = DATABASE()
  AND c.table_name IN ('goals', 'plans')
  AND c.column_name = 'target_pace'
  AND c.data_type <> 'int'
HAVING failures > 0;
