package com.neostride.server.admin.controller;

import com.neostride.server.admin.dto.AccountRestoreRequest;
import com.neostride.server.admin.dto.AccountSuspendRequest;
import com.neostride.server.admin.dto.AdminUserAccountResponse;
import com.neostride.server.admin.security.OperatorAuthorizationService;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.service.AdminAccountService;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.platform.web.CursorSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {
	private final AdminAccountService service;
	private final OperatorAuthorizationService authorizationService;

	public AdminAccountController(AdminAccountService service, OperatorAuthorizationService authorizationService) {
		this.service = service;
		this.authorizationService = authorizationService;
	}

	@GetMapping
	public ResponseEntity<List<AdminUserAccountResponse>> search(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "q", required = false) String query,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to,
			@RequestParam(value = "limit", defaultValue = "50") int limit
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.ACCOUNT_READ);
		var page = service.searchPage(query, status, cursor, from, to, limit);
		return ResponseEntity.ok().headers(CursorSupport.headers(page)).body(page.items());
	}

	@GetMapping("/{userId}")
	public ResponseEntity<AdminUserAccountResponse> get(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long userId
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.ACCOUNT_READ);
		return ResponseEntity.ok(service.get(userId));
	}

	@PostMapping("/{userId}/suspend")
	public ResponseEntity<AdminUserAccountResponse> suspend(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long userId,
			@RequestBody AccountSuspendRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.ACCOUNT_SUSPEND);
		return ResponseEntity.ok(service.suspend(userId, request, actor, AuditContext.from(servletRequest)));
	}

	@PostMapping("/{userId}/restore")
	public ResponseEntity<AdminUserAccountResponse> restore(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long userId,
			@RequestBody(required = false) AccountRestoreRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.ACCOUNT_SUSPEND);
		return ResponseEntity.ok(service.restore(userId, request, actor, AuditContext.from(servletRequest)));
	}
}
