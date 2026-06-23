package com.neostride.server.admin.controller;

import com.neostride.server.admin.security.OperatorAuthorizationService;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.audit.dto.AuditLogResponse;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.platform.web.CursorSupport;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {
	private final AuditLogService auditLogService;
	private final OperatorAuthorizationService authorizationService;

	public AdminAuditLogController(AuditLogService auditLogService, OperatorAuthorizationService authorizationService) {
		this.auditLogService = auditLogService;
		this.authorizationService = authorizationService;
	}

	@GetMapping
	public ResponseEntity<List<AuditLogResponse>> search(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "action", required = false) String action,
			@RequestParam(value = "target_type", required = false) String targetType,
			@RequestParam(value = "target_id", required = false) String targetId,
			@RequestParam(value = "actor_operator_account_id", required = false) Long actorOperatorAccountId,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to,
			@RequestParam(value = "limit", defaultValue = "50") int limit
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.AUDIT_READ);
		var page = auditLogService.searchPage(action, targetType, targetId, actorOperatorAccountId, cursor, from, to, limit);
		return ResponseEntity.ok().headers(CursorSupport.headers(page)).body(page.items());
	}

	@GetMapping("/{auditLogId}")
	public ResponseEntity<AuditLogResponse> get(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long auditLogId
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.AUDIT_READ);
		return ResponseEntity.ok(auditLogService.get(auditLogId));
	}
}
