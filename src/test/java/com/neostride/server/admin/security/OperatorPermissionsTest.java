package com.neostride.server.admin.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorPermissionsTest {
	@Test
	void developerCanTriageBugReportsButAuditorCannot() {
		assertThat(OperatorPermissions.defaultsForRole("DEVELOPER"))
				.contains(OperatorPermissions.LOGS_READ, OperatorPermissions.BUG_REPORT_WRITE)
				.doesNotContain(OperatorPermissions.OPERATOR_MANAGE);
		assertThat(OperatorPermissions.defaultsForRole("AUDITOR"))
				.contains(OperatorPermissions.LOGS_READ, OperatorPermissions.AUDIT_READ)
				.doesNotContain(OperatorPermissions.BUG_REPORT_WRITE, OperatorPermissions.OPERATOR_MANAGE);
	}

	@Test
	void operatorAdminCanManageOperators() {
		assertThat(OperatorPermissions.defaultsForRole("OPERATOR_ADMIN"))
				.contains(OperatorPermissions.OPERATOR_MANAGE, OperatorPermissions.BUG_REPORT_WRITE);
	}
}
