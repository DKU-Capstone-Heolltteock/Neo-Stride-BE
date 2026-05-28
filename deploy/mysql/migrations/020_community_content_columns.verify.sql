-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '020_missing_community_content_columns' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'title' AS column_name UNION ALL
    SELECT 'body_text' UNION ALL
    SELECT 'route_map_image_url' UNION ALL
    SELECT 'course_address' UNION ALL
    SELECT 'distance_km' UNION ALL
    SELECT 'running_time_text' UNION ALL
    SELECT 'pace_text'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND c.table_name = 'community_contents'
      AND c.column_name = expected.column_name
)
HAVING failures > 0;
