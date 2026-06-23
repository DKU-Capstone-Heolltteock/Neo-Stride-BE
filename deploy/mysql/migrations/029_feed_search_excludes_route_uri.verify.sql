SELECT COUNT(*) = 2 AS ok
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'community_contents'
  AND index_name = 'ft_cc_feed_text_search'
  AND index_type = 'FULLTEXT'
  AND column_name IN ('title', 'body_text');
