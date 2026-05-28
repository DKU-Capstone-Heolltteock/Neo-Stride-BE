-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '009_missing_notifications_table' AS check_name, COUNT(*) AS failures
FROM (SELECT 1) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.tables t
    WHERE t.table_schema = DATABASE()
      AND t.table_name = 'notifications'
)
HAVING failures > 0;

SELECT '009_missing_notifications_columns' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'notification_id' AS column_name UNION ALL
    SELECT 'user_id' UNION ALL
    SELECT 'notification_type' UNION ALL
    SELECT 'message' UNION ALL
    SELECT 'endpoint' UNION ALL
    SELECT 'is_read' UNION ALL
    SELECT 'created_at'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND c.table_name = 'notifications'
      AND c.column_name = expected.column_name
)
HAVING failures > 0;

SELECT '009_missing_notifications_indexes' AS check_name, COUNT(*) AS failures
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
