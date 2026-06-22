DROP TABLE IF EXISTS bug_reports;
DROP TABLE IF EXISTS operator_alert_rules;
DROP TABLE IF EXISTS server_error_events;
DROP TABLE IF EXISTS api_request_metrics;
DROP TABLE IF EXISTS operator_broadcasts;
DROP TABLE IF EXISTS admin_reports;
DROP TABLE IF EXISTS operator_audit_logs;

ALTER TABLE users
    DROP FOREIGN KEY fk_users_suspended_by_operator,
    DROP KEY idx_users_suspended_by_operator,
    DROP KEY idx_users_account_status_created,
    DROP COLUMN suspended_by_operator_id,
    DROP COLUMN suspended_reason,
    DROP COLUMN suspended_until,
    DROP COLUMN suspended_at,
    DROP COLUMN account_status;

DROP TABLE IF EXISTS operator_refresh_tokens;
DROP TABLE IF EXISTS operator_account_permissions;
DROP TABLE IF EXISTS operator_accounts;
