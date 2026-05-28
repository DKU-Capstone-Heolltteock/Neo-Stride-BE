-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '016_stats_missing_rows' AS check_name, COUNT(*) AS failures
FROM community_contents cc
LEFT JOIN community_content_stats stats ON stats.content_id = cc.content_id
WHERE stats.content_id IS NULL
HAVING failures > 0;

SELECT '016_missing_content_insert_stats_trigger' AS check_name, COUNT(*) AS failures
FROM (SELECT 1) marker
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.triggers t
    WHERE t.trigger_schema = DATABASE()
      AND t.trigger_name = 'trg_cc_after_insert_stats'
      AND t.event_object_table = 'community_contents'
      AND t.event_manipulation = 'INSERT'
)
HAVING failures > 0;
