SELECT '025_users_search_email_still_indexed' AS check_name, COUNT(*) AS failures
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND index_name = 'ft_users_search'
  AND column_name = 'email'
HAVING failures > 0;

SELECT '025_users_search_missing_expected_columns' AS check_name, 2 - COUNT(DISTINCT column_name) AS failures
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND index_name = 'ft_users_search'
  AND column_name IN ('name', 'community_profile_name')
HAVING failures > 0;
