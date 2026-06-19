SELECT 'missing operator_accounts' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'operator_accounts'
);
SELECT 'missing operator_account_permissions' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'operator_account_permissions'
);
SELECT 'missing operator_refresh_tokens' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'operator_refresh_tokens'
);
SELECT 'missing operator_audit_logs' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'operator_audit_logs'
);
SELECT 'missing admin_reports' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'admin_reports'
);
SELECT 'missing operator_broadcasts' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'operator_broadcasts'
);
SELECT 'missing api_request_metrics' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'api_request_metrics'
);
SELECT 'missing server_error_events' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'server_error_events'
);
SELECT 'missing operator_alert_rules' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'operator_alert_rules'
);
SELECT 'missing bug_reports' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'bug_reports'
);
SELECT 'missing users.account_status' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'account_status'
);
SELECT 'missing users.suspended_reason' WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'suspended_reason'
);
