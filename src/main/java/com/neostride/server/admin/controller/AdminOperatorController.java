package com.neostride.server.admin.controller;

import com.neostride.server.admin.dto.OperatorAccountResponse;
import com.neostride.server.admin.dto.OperatorCreateRequest;
import com.neostride.server.admin.dto.OperatorPermissionCatalogResponse;
import com.neostride.server.admin.dto.OperatorPermissionsUpdateRequest;
import com.neostride.server.admin.dto.OperatorStatusUpdateRequest;
import com.neostride.server.admin.dto.OperatorUpdateRequest;
import com.neostride.server.admin.security.OperatorAuthorizationService;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.service.OperatorManagementService;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.platform.web.CursorSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operators")
public class AdminOperatorController {
	private final OperatorManagementService service;
	private final OperatorAuthorizationService authorizationService;

	public AdminOperatorController(OperatorManagementService service, OperatorAuthorizationService authorizationService) {
		this.service = service;
		this.authorizationService = authorizationService;
	}

	@GetMapping
	public ResponseEntity<List<OperatorAccountResponse>> list(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "role", required = false) String role,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to,
			@RequestParam(value = "limit", defaultValue = "50") int limit
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.OPERATOR_MANAGE);
		var page = service.listPage(role, status, cursor, from, to, limit);
		return ResponseEntity.ok().headers(CursorSupport.headers(page)).body(page.items());
	}

	@GetMapping("/permissions")
	public ResponseEntity<OperatorPermissionCatalogResponse> permissions(
			@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.OPERATOR_MANAGE);
		return ResponseEntity.ok(service.permissionCatalog());
	}

	@GetMapping("/{operatorAccountId}")
	public ResponseEntity<OperatorAccountResponse> get(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long operatorAccountId
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.OPERATOR_MANAGE);
		return ResponseEntity.ok(service.get(operatorAccountId));
	}

	@PostMapping
	public ResponseEntity<OperatorAccountResponse> create(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody OperatorCreateRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.OPERATOR_MANAGE);
		return ResponseEntity.ok(service.create(request, actor, AuditContext.from(servletRequest)));
	}

	@PatchMapping("/{operatorAccountId}")
	public ResponseEntity<OperatorAccountResponse> update(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long operatorAccountId,
			@RequestBody OperatorUpdateRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.OPERATOR_MANAGE);
		return ResponseEntity.ok(service.updateAccount(operatorAccountId, request, actor, AuditContext.from(servletRequest)));
	}

	@PatchMapping("/{operatorAccountId}/status")
	public ResponseEntity<OperatorAccountResponse> updateStatus(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long operatorAccountId,
			@RequestBody OperatorStatusUpdateRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.OPERATOR_MANAGE);
		return ResponseEntity.ok(service.updateStatus(operatorAccountId, request, actor, AuditContext.from(servletRequest)));
	}

	@PutMapping("/{operatorAccountId}/permissions")
	public ResponseEntity<OperatorAccountResponse> updatePermissions(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long operatorAccountId,
			@RequestBody OperatorPermissionsUpdateRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.OPERATOR_MANAGE);
		return ResponseEntity.ok(service.updatePermissions(operatorAccountId, request, actor, AuditContext.from(servletRequest)));
	}
}
