-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '023_missing_notifications_indexes' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'idx_notifications_user_created' AS index_name UNION ALL
    SELECT 'idx_notifications_user_read'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = 'notifications'
      AND s.index_name = expected.index_name
)
HAVING failures > 0;
