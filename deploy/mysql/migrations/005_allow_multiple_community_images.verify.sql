-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '005_missing_community_contents_image' AS check_name, COUNT(*) AS failures
FROM (SELECT 1) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND c.table_name = 'community_contents'
      AND c.column_name = 'image'
      AND c.is_nullable = 'YES'
)
HAVING failures > 0;
