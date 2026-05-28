-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '013_missing_indexes' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'community_interactions' AS table_name, 'idx_ci_content_type_created' AS index_name
    UNION ALL SELECT 'running_records', 'idx_rr_user_created'
    UNION ALL SELECT 'gps_traces', 'idx_gps_record_time'
    UNION ALL SELECT 'goals', 'idx_goals_user_active_created'
    UNION ALL SELECT 'plans', 'idx_plans_user_goal_date'
    UNION ALL SELECT 'plans', 'idx_plans_goal_date'
    UNION ALL SELECT 'plans', 'idx_plans_user_date'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = expected.table_name
      AND s.index_name = expected.index_name
)
HAVING failures > 0;
