-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '010_missing_coaching_target_pace' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'goals' AS table_name UNION ALL
    SELECT 'plans'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND c.table_name = expected.table_name
      AND c.column_name = 'target_pace'
      AND c.data_type IN ('decimal', 'int')
)
HAVING failures > 0;
