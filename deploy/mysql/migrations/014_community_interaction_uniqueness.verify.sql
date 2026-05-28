-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '014_duplicate_actions' AS check_name, COUNT(*) AS failures
FROM (
    SELECT user_id, content_id, interaction_type
    FROM community_interactions
    WHERE interaction_type IN ('LIKE', 'BOOKMARK')
    GROUP BY user_id, content_id, interaction_type
    HAVING COUNT(*) > 1
) duplicate_actions
HAVING failures > 0;

SELECT '014_duplicate_tags' AS check_name, COUNT(*) AS failures
FROM (
    SELECT content_id, tagged_user_id
    FROM community_interactions
    WHERE interaction_type = 'TAG' AND tagged_user_id IS NOT NULL
    GROUP BY content_id, tagged_user_id
    HAVING COUNT(*) > 1
) duplicate_tags
HAVING failures > 0;

SELECT '014_missing_generated_columns' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'action_user_id' AS column_name
    UNION ALL SELECT 'action_tagged_user_id'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND c.table_name = 'community_interactions'
      AND c.column_name = expected.column_name
)
HAVING failures > 0;

SELECT '014_missing_unique_indexes' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'uq_ci_action_user' AS index_name
    UNION ALL SELECT 'uq_ci_tagged_user'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = 'community_interactions'
      AND s.index_name = expected.index_name
      AND s.non_unique = 0
)
HAVING failures > 0;
