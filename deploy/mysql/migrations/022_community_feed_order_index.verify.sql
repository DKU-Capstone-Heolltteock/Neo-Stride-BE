-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '022_missing_feed_order_index' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 1
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = 'community_contents'
      AND s.index_name = 'idx_cc_type_created'
)
HAVING failures > 0;
