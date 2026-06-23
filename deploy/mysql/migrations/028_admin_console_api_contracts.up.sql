CREATE INDEX idx_operator_accounts_created_cursor
    ON operator_accounts (created_at DESC, operator_account_id DESC);

CREATE INDEX idx_users_created_cursor
    ON users (created_at DESC, user_id DESC);

CREATE INDEX idx_operator_audit_logs_created_cursor
    ON operator_audit_logs (created_at DESC, operator_audit_log_id DESC);

CREATE INDEX idx_admin_reports_created_cursor
    ON admin_reports (created_at DESC, report_id DESC);

CREATE INDEX idx_bug_reports_created_cursor
    ON bug_reports (created_at DESC, bug_report_id DESC);

CREATE INDEX idx_server_error_events_created_cursor
    ON server_error_events (created_at DESC, server_error_event_id DESC);

CREATE INDEX idx_operator_alert_rules_created_cursor
    ON operator_alert_rules (created_at DESC, alert_rule_id DESC);
