-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '029_missing_feed_search_fulltext_columns' AS check_name, 2 - COUNT(DISTINCT column_name) AS failures
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'community_contents'
  AND index_name = 'ft_cc_feed_text_search'
  AND index_type = 'FULLTEXT'
  AND column_name IN ('title', 'body_text')
HAVING failures > 0;
