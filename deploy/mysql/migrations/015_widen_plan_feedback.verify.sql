-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '015_missing_plans_feedback_column' AS check_name, COUNT(*) AS failures
FROM (SELECT 1) marker
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND c.table_name = 'plans'
      AND c.column_name = 'feedback'
)
HAVING failures > 0;

SELECT '015_plans_feedback_not_text' AS check_name, COUNT(*) AS failures
FROM information_schema.columns c
WHERE c.table_schema = DATABASE()
  AND c.table_name = 'plans'
  AND c.column_name = 'feedback'
  AND c.data_type NOT IN ('text', 'mediumtext', 'longtext')
HAVING failures > 0;
