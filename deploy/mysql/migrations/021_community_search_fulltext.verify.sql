-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '021_missing_fulltext_indexes' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'community_contents' AS table_name, 'ft_cc_content_search' AS index_name UNION ALL
    SELECT 'users', 'ft_users_search' UNION ALL
    SELECT 'community_users', 'ft_cu_search'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = expected.table_name
      AND s.index_name = expected.index_name
      AND s.index_type = 'FULLTEXT'
)
HAVING failures > 0;
