SELECT '028_missing_operator_accounts_created_cursor' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'operator_accounts'
      AND index_name = 'idx_operator_accounts_created_cursor'
);

SELECT '028_missing_users_created_cursor' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND index_name = 'idx_users_created_cursor'
);

SELECT '028_missing_operator_audit_logs_created_cursor' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'operator_audit_logs'
      AND index_name = 'idx_operator_audit_logs_created_cursor'
);

SELECT '028_missing_admin_reports_created_cursor' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'admin_reports'
      AND index_name = 'idx_admin_reports_created_cursor'
);

SELECT '028_missing_bug_reports_created_cursor' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bug_reports'
      AND index_name = 'idx_bug_reports_created_cursor'
);

SELECT '028_missing_server_error_events_created_cursor' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'server_error_events'
      AND index_name = 'idx_server_error_events_created_cursor'
);

SELECT '028_missing_operator_alert_rules_created_cursor' AS check_name, 1 AS failures
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'operator_alert_rules'
      AND index_name = 'idx_operator_alert_rules_created_cursor'
);
