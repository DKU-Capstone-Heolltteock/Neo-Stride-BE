package com.neostride.server.admin.security;

import java.util.Collections;
import java.util.LinkedHashMap;
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
	public static final String BUG_REPORT_WRITE = "bug-report:write";
	public static final String ALERT_POLICY_WRITE = "alert-policy:write";
	public static final String AUDIT_READ = "audit:read";
	public static final String OPERATOR_MANAGE = "operator:manage";

	private static final List<String> ALL = List.of(
			ACCOUNT_READ,
			ACCOUNT_SUSPEND,
			REPORT_READ,
			REPORT_RESOLVE,
			NOTIFICATION_SEND,
			METRICS_READ,
			LOGS_READ,
			BUG_REPORT_WRITE,
			ALERT_POLICY_WRITE,
			AUDIT_READ,
			OPERATOR_MANAGE
	);

	private static final List<String> ROLES = List.of(
			"SUPER_ADMIN",
			"OPERATOR_ADMIN",
			"MODERATOR",
			"SUPPORT",
			"DEVELOPER",
			"AUDITOR"
	);

	private static final Map<String, List<String>> ROLE_DEFAULTS = createRoleDefaults();

	private OperatorPermissions() {
	}

	public static List<String> all() {
		return ALL;
	}

	public static List<String> roles() {
		return ROLES;
	}

	public static Map<String, List<String>> roleDefaults() {
		return ROLE_DEFAULTS;
	}

	public static List<String> defaultsForRole(String role) {
		return ROLE_DEFAULTS.getOrDefault(role, List.of());
	}

	private static Map<String, List<String>> createRoleDefaults() {
		Map<String, List<String>> defaults = new LinkedHashMap<>();
		defaults.put("SUPER_ADMIN", ALL);
		defaults.put("OPERATOR_ADMIN", List.of(
				ACCOUNT_READ,
				ACCOUNT_SUSPEND,
				REPORT_READ,
				REPORT_RESOLVE,
				NOTIFICATION_SEND,
				METRICS_READ,
				BUG_REPORT_WRITE,
				ALERT_POLICY_WRITE,
				AUDIT_READ,
				OPERATOR_MANAGE
		));
		defaults.put("MODERATOR", List.of(ACCOUNT_READ, ACCOUNT_SUSPEND, REPORT_READ, REPORT_RESOLVE));
		defaults.put("SUPPORT", List.of(ACCOUNT_READ, REPORT_READ, NOTIFICATION_SEND));
		defaults.put("DEVELOPER", List.of(METRICS_READ, LOGS_READ));
		defaults.put("AUDITOR", List.of(ACCOUNT_READ, REPORT_READ, METRICS_READ, LOGS_READ, AUDIT_READ));
		return Collections.unmodifiableMap(defaults);
	}
}
