-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '006_missing_query_indexes' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'community_contents' AS table_name, 'idx_cc_feed_list' AS index_name UNION ALL
    SELECT 'community_contents', 'idx_cc_author_type_created' UNION ALL
    SELECT 'community_interactions', 'idx_ci_content_type' UNION ALL
    SELECT 'community_interactions', 'idx_ci_user_type_content' UNION ALL
    SELECT 'community_interactions', 'idx_ci_tagged_type_content' UNION ALL
    SELECT 'relationships', 'idx_rel_user1_status_user2' UNION ALL
    SELECT 'relationships', 'idx_rel_user2_status_user1'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = expected.table_name
      AND s.index_name = expected.index_name
)
HAVING failures > 0;
