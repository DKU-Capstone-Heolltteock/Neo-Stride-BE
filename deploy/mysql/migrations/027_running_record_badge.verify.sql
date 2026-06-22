SELECT '027_running_records_missing_badge' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'running_records'
      AND column_name = 'badge'
);

SELECT '027_running_records_missing_badge_index' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'running_records'
      AND index_name = 'idx_rr_user_badge_distance'
);
