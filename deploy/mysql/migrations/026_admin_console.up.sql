CREATE TABLE IF NOT EXISTS operator_accounts (
    operator_account_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role ENUM('SUPER_ADMIN', 'OPERATOR_ADMIN', 'MODERATOR', 'SUPPORT', 'DEVELOPER', 'AUDITOR') NOT NULL,
    status ENUM('ACTIVE', 'DISABLED') NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (operator_account_id),
    UNIQUE KEY uq_operator_accounts_email (email),
    KEY idx_operator_accounts_status_role (status, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS operator_account_permissions (
    operator_account_id BIGINT NOT NULL,
    permission VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (operator_account_id, permission),
    KEY idx_operator_account_permissions_permission (permission),
    CONSTRAINT fk_operator_account_permissions_account
        FOREIGN KEY (operator_account_id) REFERENCES operator_accounts (operator_account_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS operator_refresh_tokens (
    operator_refresh_token_id BIGINT NOT NULL AUTO_INCREMENT,
    operator_account_id BIGINT NOT NULL,
    token_id_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (operator_refresh_token_id),
    UNIQUE KEY uq_operator_refresh_tokens_hash (token_id_hash),
    KEY idx_operator_refresh_tokens_operator_active (operator_account_id, revoked_at, expires_at),
    CONSTRAINT fk_operator_refresh_tokens_operator
        FOREIGN KEY (operator_account_id) REFERENCES operator_accounts (operator_account_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE users
    ADD COLUMN account_status ENUM('ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE' AFTER profile_photo,
    ADD COLUMN suspended_at DATETIME NULL AFTER account_status,
    ADD COLUMN suspended_until DATETIME NULL AFTER suspended_at,
    ADD COLUMN suspended_reason VARCHAR(1000) NULL AFTER suspended_until,
    ADD COLUMN suspended_by_operator_id BIGINT NULL AFTER suspended_reason,
    ADD KEY idx_users_account_status_created (account_status, created_at),
    ADD KEY idx_users_suspended_by_operator (suspended_by_operator_id),
    ADD CONSTRAINT fk_users_suspended_by_operator
        FOREIGN KEY (suspended_by_operator_id) REFERENCES operator_accounts (operator_account_id)
        ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS operator_audit_logs (
    operator_audit_log_id BIGINT NOT NULL AUTO_INCREMENT,
    actor_operator_account_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(120) NULL,
    reason VARCHAR(1000) NULL,
    before_summary TEXT NULL,
    after_summary TEXT NULL,
    request_id VARCHAR(120) NULL,
    ip_address VARCHAR(80) NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (operator_audit_log_id),
    KEY idx_operator_audit_logs_actor_created (actor_operator_account_id, created_at DESC),
    KEY idx_operator_audit_logs_target_created (target_type, target_id, created_at DESC),
    KEY idx_operator_audit_logs_action_created (action, created_at DESC),
    CONSTRAINT fk_operator_audit_logs_actor
        FOREIGN KEY (actor_operator_account_id) REFERENCES operator_accounts (operator_account_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_reports (
    report_id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT NULL,
    target_user_id BIGINT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(120) NULL,
    category VARCHAR(80) NOT NULL,
    status ENUM('PENDING', 'RESOLVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(1000) NOT NULL,
    resolution VARCHAR(1000) NULL,
    assigned_operator_account_id BIGINT NULL,
    resolved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (report_id),
    KEY idx_admin_reports_status_created (status, created_at DESC),
    KEY idx_admin_reports_target (target_type, target_id),
    KEY idx_admin_reports_reporter (reporter_user_id, created_at DESC),
    KEY idx_admin_reports_target_user (target_user_id, created_at DESC),
    CONSTRAINT fk_admin_reports_reporter
        FOREIGN KEY (reporter_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_admin_reports_target_user
        FOREIGN KEY (target_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_admin_reports_assigned_operator
        FOREIGN KEY (assigned_operator_account_id) REFERENCES operator_accounts (operator_account_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS operator_broadcasts (
    broadcast_id BIGINT NOT NULL AUTO_INCREMENT,
    sender_operator_account_id BIGINT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    target_type ENUM('ALL', 'USER') NOT NULL DEFAULT 'ALL',
    target_user_id BIGINT NULL,
    recipient_count INT NOT NULL DEFAULT 0,
    status ENUM('SENT', 'PARTIAL', 'FAILED') NOT NULL DEFAULT 'SENT',
    discord_status VARCHAR(40) NULL,
    discord_error VARCHAR(1000) NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (broadcast_id),
    KEY idx_operator_broadcasts_sender_created (sender_operator_account_id, created_at DESC),
    KEY idx_operator_broadcasts_created (created_at DESC),
    CONSTRAINT fk_operator_broadcasts_sender
        FOREIGN KEY (sender_operator_account_id) REFERENCES operator_accounts (operator_account_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_operator_broadcasts_target_user
        FOREIGN KEY (target_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS api_request_metrics (
    api_request_metric_id BIGINT NOT NULL AUTO_INCREMENT,
    method VARCHAR(10) NOT NULL,
    path VARCHAR(255) NOT NULL,
    status_code INT NOT NULL,
    duration_ms BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (api_request_metric_id),
    KEY idx_api_request_metrics_occurred (occurred_at DESC),
    KEY idx_api_request_metrics_path_status (path, status_code, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS server_error_events (
    server_error_event_id BIGINT NOT NULL AUTO_INCREMENT,
    method VARCHAR(10) NOT NULL,
    path VARCHAR(255) NOT NULL,
    status_code INT NOT NULL,
    error_type VARCHAR(120) NOT NULL,
    message_summary VARCHAR(500) NULL,
    request_id VARCHAR(120) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (server_error_event_id),
    KEY idx_server_error_events_created (created_at DESC),
    KEY idx_server_error_events_path_created (path, created_at DESC),
    KEY idx_server_error_events_status_created (status_code, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS operator_alert_rules (
    alert_rule_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    metric_type ENUM('API_ERROR_RATE', 'API_TRAFFIC', 'SERVER_ERROR_COUNT') NOT NULL,
    threshold_value DECIMAL(12,2) NOT NULL,
    window_minutes INT NOT NULL,
    channel ENUM('DISCORD') NOT NULL DEFAULT 'DISCORD',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    discord_status VARCHAR(40) NULL,
    discord_error VARCHAR(1000) NULL,
    last_tested_at DATETIME NULL,
    created_by_operator_account_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (alert_rule_id),
    KEY idx_operator_alert_rules_enabled_metric (enabled, metric_type),
    CONSTRAINT fk_operator_alert_rules_creator
        FOREIGN KEY (created_by_operator_account_id) REFERENCES operator_accounts (operator_account_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bug_reports (
    bug_report_id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status ENUM('OPEN', 'TRIAGED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED') NOT NULL DEFAULT 'OPEN',
    severity ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    app_version VARCHAR(80) NULL,
    device_model VARCHAR(120) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (bug_report_id),
    KEY idx_bug_reports_status_created (status, created_at DESC),
    KEY idx_bug_reports_reporter_created (reporter_user_id, created_at DESC),
    CONSTRAINT fk_bug_reports_reporter
        FOREIGN KEY (reporter_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
