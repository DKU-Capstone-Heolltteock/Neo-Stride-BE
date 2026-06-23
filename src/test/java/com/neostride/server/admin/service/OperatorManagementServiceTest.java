package com.neostride.server.admin.service;

import com.neostride.server.admin.dto.OperatorAccountResponse;
import com.neostride.server.admin.dto.OperatorCreateRequest;
import com.neostride.server.admin.dto.OperatorPermissionsUpdateRequest;
import com.neostride.server.admin.dto.OperatorStatusUpdateRequest;
import com.neostride.server.admin.dto.OperatorUpdateRequest;
import com.neostride.server.admin.repository.OperatorRepository;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.auth.exception.ForbiddenException;
import com.neostride.server.auth.service.PasswordHashService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperatorManagementServiceTest {
	private final OperatorRepository operatorRepository = mock(OperatorRepository.class);
	private final PasswordHashService passwordHashService = mock(PasswordHashService.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);
	private final OperatorManagementService service = new OperatorManagementService(operatorRepository, passwordHashService, auditLogService);
	private final OperatorPrincipal actor = new OperatorPrincipal(1L, "admin@example.com", "Admin", "OPERATOR_ADMIN", List.of(OperatorPermissions.OPERATOR_MANAGE));
	private final OperatorPrincipal superAdmin = new OperatorPrincipal(10L, "root@example.com", "Root", "SUPER_ADMIN", List.of(OperatorPermissions.OPERATOR_MANAGE));
	private final AuditContext context = new AuditContext("req-1", "127.0.0.1", "JUnit");

	@Test
	void createHashesPasswordAndAudits() {
		OperatorCreateRequest request = new OperatorCreateRequest("New@Example.com", "secret", "New Admin", "developer", List.of(OperatorPermissions.LOGS_READ), "onboarding");
		OperatorAccountResponse created = account(2L, "new@example.com", "DEVELOPER", "ACTIVE", List.of(OperatorPermissions.LOGS_READ));
		when(passwordHashService.hash("secret")).thenReturn("hash");
		when(operatorRepository.createAccount("new@example.com", "hash", "New Admin", "DEVELOPER", "ACTIVE", List.of(OperatorPermissions.LOGS_READ)))
				.thenReturn(created);

		OperatorAccountResponse response = service.create(request, superAdmin, context);

		assertThat(response).isEqualTo(created);
		verify(passwordHashService).hash("secret");
		verify(auditLogService).record(eq(10L), eq("operator.create"), eq("operator_account"), eq("2"),
				eq("onboarding"), isNull(), contains("role=DEVELOPER"), eq(context));
	}

	@Test
	void updateAccountChangesProfileAndAudits() {
		OperatorAccountResponse before = account(2L, "old@example.com", "SUPPORT", "ACTIVE", List.of());
		OperatorAccountResponse after = account(2L, "new@example.com", "MODERATOR", "ACTIVE", List.of());
		when(operatorRepository.findAccount(2L)).thenReturn(Optional.of(before));
		when(operatorRepository.updateAccount(2L, "new@example.com", "New Name", "MODERATOR")).thenReturn(after);

		OperatorAccountResponse response = service.updateAccount(2L, new OperatorUpdateRequest("New@Example.com", "New Name", "moderator", "profile correction"), actor, context);

		assertThat(response).isEqualTo(after);
		verify(operatorRepository).updateAccount(2L, "new@example.com", "New Name", "MODERATOR");
		verify(auditLogService).record(eq(1L), eq("operator.profile-update"), eq("operator_account"), eq("2"),
				eq("profile correction"), contains("email=old@example.com"), contains("role=MODERATOR"), eq(context));
	}

	@Test
	void updateAccountRequiresReason() {
		assertThatThrownBy(() -> service.updateAccount(2L, new OperatorUpdateRequest("new@example.com", null, null, " "), actor, context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reason");
		verify(operatorRepository, never()).updateAccount(eq(2L), isA(String.class), isA(String.class), isA(String.class));
	}

	@Test
	void updateStatusRejectsSelfDisable() {
		when(operatorRepository.findAccount(1L)).thenReturn(Optional.of(account(1L, "admin@example.com", "OPERATOR_ADMIN", "ACTIVE", List.of())));

		assertThatThrownBy(() -> service.updateStatus(1L, new OperatorStatusUpdateRequest("DISABLED", "rotation"), actor, context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("자기 자신의 운영자 계정");
		verify(operatorRepository, never()).updateStatus(eq(1L), eq("DISABLED"));
	}

	@Test
	void operatorAdminCannotCreateDeveloperBecauseRoleGrantsLogsRead() {
		OperatorCreateRequest request = new OperatorCreateRequest("dev@example.com", "secret", "Dev", "DEVELOPER", List.of(), "onboarding");

		assertThatThrownBy(() -> service.create(request, actor, context))
				.isInstanceOf(ForbiddenException.class)
				.hasMessageContaining("보유하지 않은 권한");
		verify(passwordHashService, never()).hash("secret");
	}

	@Test
	void operatorAdminCannotCreateAuditorBecauseRoleGrantsLogsRead() {
		OperatorCreateRequest request = new OperatorCreateRequest("auditor@example.com", "secret", "Auditor", "AUDITOR", List.of(), "onboarding");

		assertThatThrownBy(() -> service.create(request, actor, context))
				.isInstanceOf(ForbiddenException.class)
				.hasMessageContaining("보유하지 않은 권한");
		verify(passwordHashService, never()).hash("secret");
	}

	@Test
	void operatorAdminCanCreateSubsetRole() {
		OperatorCreateRequest request = new OperatorCreateRequest("support@example.com", "secret", "Support", "SUPPORT", List.of(), "onboarding");
		OperatorAccountResponse created = account(3L, "support@example.com", "SUPPORT", "ACTIVE", List.of());
		when(passwordHashService.hash("secret")).thenReturn("hash");
		when(operatorRepository.createAccount("support@example.com", "hash", "Support", "SUPPORT", "ACTIVE", List.of()))
				.thenReturn(created);

		OperatorAccountResponse response = service.create(request, actor, context);

		assertThat(response).isEqualTo(created);
		verify(auditLogService).record(eq(1L), eq("operator.create"), eq("operator_account"), eq("3"),
				eq("onboarding"), isNull(), contains("role=SUPPORT"), eq(context));
	}

	@Test
	void operatorAdminCannotCreateProtectedRoles() {
		OperatorCreateRequest request = new OperatorCreateRequest("root@example.com", "secret", "Root", "SUPER_ADMIN", List.of(), "bootstrap");

		assertThatThrownBy(() -> service.create(request, actor, context))
				.isInstanceOf(ForbiddenException.class)
				.hasMessageContaining("역할을 생성할 권한");
		verify(passwordHashService, never()).hash("secret");
	}

	@Test
	void operatorAdminCannotDisableProtectedRoles() {
		when(operatorRepository.findAccount(10L)).thenReturn(Optional.of(account(10L, "root@example.com", "SUPER_ADMIN", "ACTIVE", List.of())));

		assertThatThrownBy(() -> service.updateStatus(10L, new OperatorStatusUpdateRequest("DISABLED", "rotation"), actor, context))
				.isInstanceOf(ForbiddenException.class)
				.hasMessageContaining("계정을 관리할 권한");
		verify(operatorRepository, never()).updateStatus(eq(10L), eq("DISABLED"));
	}

	@Test
	void operatorAdminCannotGrantPermissionOutsideEffectiveSet() {
		when(operatorRepository.findAccount(2L)).thenReturn(Optional.of(account(2L, "mod@example.com", "MODERATOR", "ACTIVE", List.of())));

		assertThatThrownBy(() -> service.updatePermissions(2L,
				new OperatorPermissionsUpdateRequest(List.of(OperatorPermissions.LOGS_READ), "grant"), actor, context))
				.isInstanceOf(ForbiddenException.class)
				.hasMessageContaining("보유하지 않은 권한");
		verify(operatorRepository, never()).replacePermissions(eq(2L), org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	void operatorAdminCanGrantPermissionInsideEffectiveSet() {
		OperatorAccountResponse before = account(2L, "mod@example.com", "MODERATOR", "ACTIVE", List.of());
		OperatorAccountResponse after = account(2L, "mod@example.com", "MODERATOR", "ACTIVE", List.of(OperatorPermissions.NOTIFICATION_SEND));
		when(operatorRepository.findAccount(2L)).thenReturn(Optional.of(before));
		when(operatorRepository.replacePermissions(2L, List.of(OperatorPermissions.NOTIFICATION_SEND))).thenReturn(after);

		OperatorAccountResponse response = service.updatePermissions(2L,
				new OperatorPermissionsUpdateRequest(List.of(OperatorPermissions.NOTIFICATION_SEND), "grant"), actor, context);

		assertThat(response).isEqualTo(after);
		verify(auditLogService).record(eq(1L), eq("operator.permissions-update"), eq("operator_account"), eq("2"),
				eq("grant"), contains("explicit_permissions=0"), contains("notification:send"), eq(context));
	}

	@Test
	void operatorAdminCannotGrantOperatorManagePermission() {
		when(operatorRepository.findAccount(2L)).thenReturn(Optional.of(account(2L, "dev@example.com", "DEVELOPER", "ACTIVE", List.of())));

		assertThatThrownBy(() -> service.updatePermissions(2L,
				new OperatorPermissionsUpdateRequest(List.of(OperatorPermissions.OPERATOR_MANAGE), "grant"), actor, context))
				.isInstanceOf(ForbiddenException.class)
				.hasMessageContaining("SUPER_ADMIN");
		verify(operatorRepository, never()).replacePermissions(eq(2L), org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	void superAdminCanCreateAuditorWithSensitiveExplicitPermissions() {
		OperatorCreateRequest request = new OperatorCreateRequest("audit@example.com", "secret", "Audit", "AUDITOR",
				List.of(OperatorPermissions.LOGS_READ, OperatorPermissions.OPERATOR_MANAGE), "security team");
		OperatorAccountResponse created = account(12L, "audit@example.com", "AUDITOR", "ACTIVE",
				List.of(OperatorPermissions.LOGS_READ, OperatorPermissions.OPERATOR_MANAGE));
		when(passwordHashService.hash("secret")).thenReturn("hash");
		when(operatorRepository.createAccount("audit@example.com", "hash", "Audit", "AUDITOR", "ACTIVE",
				List.of(OperatorPermissions.LOGS_READ, OperatorPermissions.OPERATOR_MANAGE))).thenReturn(created);

		OperatorAccountResponse response = service.create(request, superAdmin, context);

		assertThat(response).isEqualTo(created);
		verify(auditLogService).record(eq(10L), eq("operator.create"), eq("operator_account"), eq("12"),
				eq("security team"), isNull(), contains("role=AUDITOR"), eq(context));
	}

	@Test
	void superAdminCanCreateOperatorAdmin() {
		OperatorCreateRequest request = new OperatorCreateRequest("Ops@Example.com", "secret", "Ops Admin", "OPERATOR_ADMIN", List.of(), "promotion");
		OperatorAccountResponse created = account(11L, "ops@example.com", "OPERATOR_ADMIN", "ACTIVE", List.of());
		when(passwordHashService.hash("secret")).thenReturn("hash");
		when(operatorRepository.createAccount("ops@example.com", "hash", "Ops Admin", "OPERATOR_ADMIN", "ACTIVE", List.of()))
				.thenReturn(created);

		OperatorAccountResponse response = service.create(request, superAdmin, context);

		assertThat(response).isEqualTo(created);
		verify(auditLogService).record(eq(10L), eq("operator.create"), eq("operator_account"), eq("11"),
				eq("promotion"), isNull(), contains("role=OPERATOR_ADMIN"), eq(context));
	}

	@Test
	void updatePermissionsRejectsUnknownPermission() {
		assertThatThrownBy(() -> service.updatePermissions(2L,
				new OperatorPermissionsUpdateRequest(List.of("logs:write"), "grant"), actor, context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("permission이 올바르지 않습니다");
		verify(operatorRepository, never()).replacePermissions(eq(2L), org.mockito.ArgumentMatchers.anyList());
	}

	private OperatorAccountResponse account(long id, String email, String role, String status, List<String> explicitPermissions) {
		List<String> rolePermissions = OperatorPermissions.defaultsForRole(role);
		return new OperatorAccountResponse(
				id,
				email,
				"Operator",
				role,
				status,
				rolePermissions,
				explicitPermissions,
				explicitPermissions,
				LocalDateTime.now(),
				LocalDateTime.now(),
				LocalDateTime.now()
		);
	}
}
