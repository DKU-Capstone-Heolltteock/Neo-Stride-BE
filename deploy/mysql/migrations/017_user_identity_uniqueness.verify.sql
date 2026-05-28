-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '017_duplicate_user_emails' AS check_name, COUNT(*) AS failures
FROM (
    SELECT email
    FROM users
    GROUP BY email
    HAVING COUNT(*) > 1
) duplicate_emails
HAVING failures > 0;

SELECT '017_duplicate_user_names' AS check_name, COUNT(*) AS failures
FROM (
    SELECT name
    FROM users
    GROUP BY name
    HAVING COUNT(*) > 1
) duplicate_names
HAVING failures > 0;

SELECT '017_duplicate_user_nicknames' AS check_name, COUNT(*) AS failures
FROM (
    SELECT community_profile_name
    FROM users
    WHERE community_profile_name IS NOT NULL
    GROUP BY community_profile_name
    HAVING COUNT(*) > 1
) duplicate_nicknames
HAVING failures > 0;

SELECT '017_duplicate_community_user_nicknames' AS check_name, COUNT(*) AS failures
FROM (
    SELECT community_profile_name
    FROM community_users
    GROUP BY community_profile_name
    HAVING COUNT(*) > 1
) duplicate_community_nicknames
HAVING failures > 0;

SELECT '017_missing_users_unique_indexes' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'uq_users_email' AS index_name
    UNION ALL SELECT 'uq_users_name'
    UNION ALL SELECT 'uq_users_community_profile_name'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = 'users'
      AND s.index_name = expected.index_name
      AND s.non_unique = 0
)
HAVING failures > 0;

SELECT '017_missing_community_users_unique_index' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'uq_community_users_community_profile_name' AS index_name
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = 'community_users'
      AND s.index_name = expected.index_name
      AND s.non_unique = 0
)
HAVING failures > 0;
