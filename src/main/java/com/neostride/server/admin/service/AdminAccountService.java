package com.neostride.server.admin.service;

import com.neostride.server.admin.dto.AccountSuspendRequest;
import com.neostride.server.admin.dto.AdminUserAccountResponse;
import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.auth.api.AdminUserAccount;
import com.neostride.server.auth.api.UserAdministrationPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountService {
	private final UserAdministrationPort userAdministrationPort;
	private final AuditLogService auditLogService;

	public AdminAccountService(UserAdministrationPort userAdministrationPort, AuditLogService auditLogService) {
		this.userAdministrationPort = userAdministrationPort;
		this.auditLogService = auditLogService;
	}

	public List<AdminUserAccountResponse> search(String query, String status, int limit) {
		return userAdministrationPort.searchAccounts(query, status, limit)
				.stream()
				.map(AdminUserAccountResponse::from)
				.toList();
	}

	public AdminUserAccountResponse get(long userId) {
		return userAdministrationPort.findAccount(userId)
				.map(AdminUserAccountResponse::from)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	@Transactional
	public AdminUserAccountResponse suspend(long userId, AccountSuspendRequest request, OperatorPrincipal actor, AuditContext context) {
		if (request == null || request.reason() == null || request.reason().isBlank()) {
			throw new IllegalArgumentException("reason은 필수입니다.");
		}
		AdminUserAccount before = userAdministrationPort.findAccount(userId)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
		AdminUserAccount after = userAdministrationPort.suspendAccount(userId, actor.operatorAccountId(), request.reason(), request.suspendedUntil());
		auditLogService.record(actor.operatorAccountId(), "account.suspend", "user", String.valueOf(userId),
				request.reason().trim(), before.status(), after.status(), context);
		return AdminUserAccountResponse.from(after);
	}

	@Transactional
	public AdminUserAccountResponse restore(long userId, OperatorPrincipal actor, AuditContext context) {
		AdminUserAccount before = userAdministrationPort.findAccount(userId)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
		AdminUserAccount after = userAdministrationPort.restoreAccount(userId);
		auditLogService.record(actor.operatorAccountId(), "account.restore", "user", String.valueOf(userId),
				"restore account", before.status(), after.status(), context);
		return AdminUserAccountResponse.from(after);
	}
}
