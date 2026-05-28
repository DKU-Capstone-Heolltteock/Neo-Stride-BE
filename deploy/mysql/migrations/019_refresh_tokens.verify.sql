-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '019_missing_refresh_tokens_table' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 1
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.tables t
    WHERE t.table_schema = DATABASE()
      AND t.table_name = 'refresh_tokens'
)
HAVING failures > 0;

SELECT '019_missing_refresh_tokens_columns' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'refresh_token_id' AS column_name UNION ALL
    SELECT 'user_id' UNION ALL
    SELECT 'token_id_hash' UNION ALL
    SELECT 'expires_at' UNION ALL
    SELECT 'revoked_at' UNION ALL
    SELECT 'created_at'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND c.table_name = 'refresh_tokens'
      AND c.column_name = expected.column_name
)
HAVING failures > 0;

SELECT '019_missing_refresh_tokens_indexes' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'uq_refresh_tokens_token_id_hash' AS index_name UNION ALL
    SELECT 'idx_refresh_tokens_user_active'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = 'refresh_tokens'
      AND s.index_name = expected.index_name
)
HAVING failures > 0;
