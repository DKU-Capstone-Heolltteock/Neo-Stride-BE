SELECT '030_missing_users_deleted_at' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'deleted_at'
);

SELECT '030_missing_users_deleted_reason' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'deleted_reason'
);

SELECT '030_missing_users_deleted_by_operator_id' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'deleted_by_operator_id'
);

SELECT '030_missing_users_deleted_at_index' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND index_name = 'idx_users_deleted_at'
);

SELECT '030_missing_users_deleted_by_operator_fk' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'users'
      AND constraint_name = 'fk_users_deleted_by_operator'
      AND referenced_table_name = 'operator_accounts'
      AND delete_rule = 'SET NULL'
);
