package com.neostride.server.admin.controller;

import com.neostride.server.admin.dto.BroadcastRequest;
import com.neostride.server.admin.dto.BroadcastResponse;
import com.neostride.server.admin.security.OperatorAuthorizationService;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.service.AdminNotificationService;
import com.neostride.server.audit.service.AuditContext;
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
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {
	private final AdminNotificationService service;
	private final OperatorAuthorizationService authorizationService;

	public AdminNotificationController(AdminNotificationService service, OperatorAuthorizationService authorizationService) {
		this.service = service;
		this.authorizationService = authorizationService;
	}

	@PostMapping("/broadcast")
	public ResponseEntity<BroadcastResponse> broadcast(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody BroadcastRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.NOTIFICATION_SEND);
		return ResponseEntity.ok(service.broadcast(request, actor, AuditContext.from(servletRequest)));
	}

	@GetMapping("/broadcasts")
	public ResponseEntity<List<BroadcastResponse>> list(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "limit", defaultValue = "50") int limit
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.NOTIFICATION_SEND);
		return ResponseEntity.ok(service.list(limit));
	}

	@GetMapping("/broadcasts/{broadcastId}")
	public ResponseEntity<BroadcastResponse> get(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long broadcastId
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.NOTIFICATION_SEND);
		return ResponseEntity.ok(service.get(broadcastId));
	}
}
