package com.neostride.server.admin.service;

import com.neostride.server.admin.dto.OperatorAccountResponse;
import com.neostride.server.admin.dto.OperatorCreateRequest;
import com.neostride.server.admin.dto.OperatorPermissionCatalogResponse;
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
import com.neostride.server.platform.web.CursorSupport;
import com.neostride.server.platform.web.CursorSupport.CursorPage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorManagementService {
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final Set<String> VALID_ROLES = Set.copyOf(OperatorPermissions.roles());
	private static final Set<String> VALID_PERMISSIONS = Set.copyOf(OperatorPermissions.all());
	private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "DISABLED");
	private static final Set<String> PROTECTED_ROLES = Set.of("SUPER_ADMIN", "OPERATOR_ADMIN");
	private static final Set<String> MANAGEMENT_PERMISSIONS = Set.of(OperatorPermissions.OPERATOR_MANAGE);

	private final OperatorRepository operatorRepository;
	private final PasswordHashService passwordHashService;
	private final AuditLogService auditLogService;

	public OperatorManagementService(
			OperatorRepository operatorRepository,
			PasswordHashService passwordHashService,
			AuditLogService auditLogService
	) {
		this.operatorRepository = operatorRepository;
		this.passwordHashService = passwordHashService;
		this.auditLogService = auditLogService;
	}

	public List<OperatorAccountResponse> list(String role, String status, int limit) {
		return listPage(role, status, null, null, null, limit).items();
	}

	public CursorPage<OperatorAccountResponse> listPage(String role, String status, String cursor, String from, String to, int limit) {
		var rows = operatorRepository.listAccounts(
				normalizeOptionalRole(role),
				normalizeOptionalStatus(status),
				CursorSupport.decode(cursor),
				CursorSupport.parseDateTime(from, "from"),
				CursorSupport.parseDateTime(to, "to"),
				CursorSupport.fetchLimit(limit)
		);
		return CursorSupport.page(rows, limit, OperatorAccountResponse::createdAt, OperatorAccountResponse::operatorAccountId);
	}

	public OperatorAccountResponse get(long operatorAccountId) {
		return operatorRepository.findAccount(operatorAccountId)
				.orElseThrow(() -> new IllegalArgumentException("운영자 계정을 찾을 수 없습니다."));
	}

	public OperatorPermissionCatalogResponse permissionCatalog() {
		return new OperatorPermissionCatalogResponse(
				OperatorPermissions.roles(),
				OperatorPermissions.all(),
				OperatorPermissions.roleDefaults()
		);
	}

	@Transactional
	public OperatorAccountResponse create(OperatorCreateRequest request, OperatorPrincipal actor, AuditContext context) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		String reason = requireReason(request.reason());
		String email = normalizeEmail(request.email());
		String name = requireText(request.name(), "name");
		String role = normalizeRole(request.role());
		List<String> explicitPermissions = normalizePermissions(request.permissions());
		requireCanCreate(actor, role, explicitPermissions);
		String passwordHash = passwordHashService.hash(request.password());
		OperatorAccountResponse created = operatorRepository.createAccount(email, passwordHash, name, role, "ACTIVE", explicitPermissions);
		auditLogService.record(actor.operatorAccountId(), "operator.create", "operator_account", String.valueOf(created.operatorAccountId()),
				reason, null, accountSummary(created), context);
		return created;
	}

	@Transactional
	public OperatorAccountResponse updateAccount(long operatorAccountId, OperatorUpdateRequest request, OperatorPrincipal actor, AuditContext context) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		String reason = requireReason(request.reason());
		OperatorAccountResponse before = get(operatorAccountId);
		requireCanManage(actor, before);
		String email = request.email() == null ? before.email() : normalizeEmail(request.email());
		String name = request.name() == null ? before.name() : requireText(request.name(), "name");
		String role = request.role() == null ? before.role() : normalizeRole(request.role());
		if (!canManageRole(actor, role)) {
			throw new ForbiddenException("해당 운영자 역할을 부여할 권한이 없습니다.");
		}
		if (!role.equals(before.role())) {
			requireTargetEffectivePermissionsWithinActor(actor, role, before.explicitPermissions());
		}
		if (email.equals(before.email()) && name.equals(before.name()) && role.equals(before.role())) {
			throw new IllegalArgumentException("수정할 운영자 정보가 없습니다.");
		}
		OperatorAccountResponse after = operatorRepository.updateAccount(operatorAccountId, email, name, role);
		auditLogService.record(actor.operatorAccountId(), "operator.profile-update", "operator_account", String.valueOf(operatorAccountId),
				reason, accountSummary(before), accountSummary(after), context);
		return after;
	}

	@Transactional
	public OperatorAccountResponse updateStatus(long operatorAccountId, OperatorStatusUpdateRequest request, OperatorPrincipal actor, AuditContext context) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		String reason = requireReason(request.reason());
		String status = normalizeStatus(request.status());
		OperatorAccountResponse before = get(operatorAccountId);
		if (actor.operatorAccountId() == operatorAccountId && "DISABLED".equals(status)) {
			throw new IllegalArgumentException("자기 자신의 운영자 계정은 비활성화할 수 없습니다.");
		}
		requireCanManage(actor, before);
		OperatorAccountResponse after = operatorRepository.updateStatus(operatorAccountId, status);
		auditLogService.record(actor.operatorAccountId(), "operator.status-update", "operator_account", String.valueOf(operatorAccountId),
				reason, before.status(), after.status(), context);
		return after;
	}

	@Transactional
	public OperatorAccountResponse updatePermissions(long operatorAccountId, OperatorPermissionsUpdateRequest request, OperatorPrincipal actor, AuditContext context) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		String reason = requireReason(request.reason());
		List<String> explicitPermissions = normalizePermissions(request.permissions());
		OperatorAccountResponse before = get(operatorAccountId);
		requireCanManage(actor, before);
		requireCanAssignPermissions(actor, explicitPermissions);
		requireTargetEffectivePermissionsWithinActor(actor, before.role(), explicitPermissions);
		OperatorAccountResponse after = operatorRepository.replacePermissions(operatorAccountId, explicitPermissions);
		auditLogService.record(actor.operatorAccountId(), "operator.permissions-update", "operator_account", String.valueOf(operatorAccountId),
				reason, permissionsSummary(before.explicitPermissions()), permissionsSummary(after.explicitPermissions()), context);
		return after;
	}

	private void requireCanCreate(OperatorPrincipal actor, String targetRole, List<String> explicitPermissions) {
		if (!canManageRole(actor, targetRole)) {
			throw new ForbiddenException("해당 운영자 역할을 생성할 권한이 없습니다.");
		}
		requireCanAssignPermissions(actor, explicitPermissions);
		requireTargetEffectivePermissionsWithinActor(actor, targetRole, explicitPermissions);
	}

	private void requireCanManage(OperatorPrincipal actor, OperatorAccountResponse target) {
		if (!canManageRole(actor, target.role())) {
			throw new ForbiddenException("해당 운영자 계정을 관리할 권한이 없습니다.");
		}
	}

	private void requireCanAssignPermissions(OperatorPrincipal actor, List<String> explicitPermissions) {
		if (!isSuperAdmin(actor) && explicitPermissions.stream().anyMatch(MANAGEMENT_PERMISSIONS::contains)) {
			throw new ForbiddenException("관리 권한 부여는 SUPER_ADMIN만 가능합니다.");
		}
	}

	private void requireTargetEffectivePermissionsWithinActor(OperatorPrincipal actor, String targetRole, List<String> explicitPermissions) {
		if (isSuperAdmin(actor)) {
			return;
		}
		Set<String> actorPermissions = effectivePermissions(actor == null ? null : actor.role(), actor == null ? null : actor.permissions());
		Set<String> targetPermissions = effectivePermissions(targetRole, explicitPermissions);
		Set<String> excessivePermissions = new LinkedHashSet<>(targetPermissions);
		excessivePermissions.removeAll(actorPermissions);
		if (!excessivePermissions.isEmpty()) {
			throw new ForbiddenException("보유하지 않은 권한은 부여할 수 없습니다.");
		}
	}

	private Set<String> effectivePermissions(String role, List<String> explicitPermissions) {
		Set<String> permissions = new LinkedHashSet<>(OperatorPermissions.defaultsForRole(role));
		if (explicitPermissions != null) {
			permissions.addAll(explicitPermissions);
		}
		return permissions;
	}

	private boolean canManageRole(OperatorPrincipal actor, String targetRole) {
		if (actor == null) {
			return false;
		}
		if (isSuperAdmin(actor)) {
			return true;
		}
		if (!"OPERATOR_ADMIN".equals(actor.role())) {
			return false;
		}
		return !PROTECTED_ROLES.contains(targetRole);
	}

	private boolean isSuperAdmin(OperatorPrincipal actor) {
		return actor != null && "SUPER_ADMIN".equals(actor.role());
	}

	private String normalizeEmail(String email) {
		String normalized = requireText(email, "email").toLowerCase(Locale.ROOT);
		if (!EMAIL_PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException("email 형식이 올바르지 않습니다.");
		}
		return normalized;
	}

	private String normalizeOptionalRole(String role) {
		return role == null || role.isBlank() ? null : normalizeRole(role);
	}

	private String normalizeRole(String role) {
		String normalized = requireText(role, "role").toUpperCase(Locale.ROOT);
		if (!VALID_ROLES.contains(normalized)) {
			throw new IllegalArgumentException("role이 올바르지 않습니다.");
		}
		return normalized;
	}

	private String normalizeOptionalStatus(String status) {
		return status == null || status.isBlank() ? null : normalizeStatus(status);
	}

	private String normalizeStatus(String status) {
		String normalized = requireText(status, "status").toUpperCase(Locale.ROOT);
		if (!VALID_STATUSES.contains(normalized)) {
			throw new IllegalArgumentException("status가 올바르지 않습니다.");
		}
		return normalized;
	}

	private List<String> normalizePermissions(List<String> permissions) {
		if (permissions == null) {
			return List.of();
		}
		Set<String> normalized = new LinkedHashSet<>();
		for (String permission : permissions) {
			String value = requireText(permission, "permission");
			if (!VALID_PERMISSIONS.contains(value)) {
				throw new IllegalArgumentException("permission이 올바르지 않습니다: " + value);
			}
			normalized.add(value);
		}
		return List.copyOf(normalized);
	}

	private String requireReason(String reason) {
		return requireText(reason, "reason");
	}

	private String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + "은 필수입니다.");
		}
		return value.trim();
	}

	private String accountSummary(OperatorAccountResponse account) {
		return "email=" + account.email()
				+ "; name=" + account.name()
				+ "; role=" + account.role()
				+ "; status=" + account.status()
				+ "; explicit_permissions=" + account.explicitPermissions().size();
	}

	private String permissionsSummary(List<String> permissions) {
		return "explicit_permissions=" + permissions.size() + "; values=" + String.join(",", permissions);
	}
}
