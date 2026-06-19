package com.neostride.server.admin.security;

import java.util.List;
import java.util.Map;

public final class OperatorPermissions {
	public static final String ACCOUNT_READ = "account:read";
	public static final String ACCOUNT_SUSPEND = "account:suspend";
	public static final String REPORT_READ = "report:read";
	public static final String REPORT_RESOLVE = "report:resolve";
	public static final String NOTIFICATION_SEND = "notification:send";
	public static final String METRICS_READ = "metrics:read";
	public static final String LOGS_READ = "logs:read";
	public static final String ALERT_POLICY_WRITE = "alert-policy:write";
	public static final String AUDIT_READ = "audit:read";

	private static final List<String> ALL = List.of(
			ACCOUNT_READ,
			ACCOUNT_SUSPEND,
			REPORT_READ,
			REPORT_RESOLVE,
			NOTIFICATION_SEND,
			METRICS_READ,
			LOGS_READ,
			ALERT_POLICY_WRITE,
			AUDIT_READ
	);

	private static final Map<String, List<String>> ROLE_DEFAULTS = Map.of(
			"SUPER_ADMIN", ALL,
			"OPERATOR_ADMIN", List.of(ACCOUNT_READ, ACCOUNT_SUSPEND, REPORT_READ, REPORT_RESOLVE, NOTIFICATION_SEND, METRICS_READ, ALERT_POLICY_WRITE, AUDIT_READ),
			"MODERATOR", List.of(ACCOUNT_READ, ACCOUNT_SUSPEND, REPORT_READ, REPORT_RESOLVE),
			"SUPPORT", List.of(ACCOUNT_READ, REPORT_READ, NOTIFICATION_SEND),
			"DEVELOPER", List.of(METRICS_READ, LOGS_READ),
			"AUDITOR", List.of(ACCOUNT_READ, REPORT_READ, METRICS_READ, LOGS_READ, AUDIT_READ)
	);

	private OperatorPermissions() {
	}

	public static List<String> all() {
		return ALL;
	}

	public static List<String> defaultsForRole(String role) {
		return ROLE_DEFAULTS.getOrDefault(role, List.of());
	}
}
