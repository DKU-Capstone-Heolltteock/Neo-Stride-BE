package com.neostride.server.admin.controller;

import com.neostride.server.admin.dto.AdminReportResponse;
import com.neostride.server.admin.dto.ReportAssignmentRequest;
import com.neostride.server.admin.dto.ReportResolveRequest;
import com.neostride.server.admin.security.OperatorAuthorizationService;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.service.AdminReportService;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.platform.web.CursorSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {
	private final AdminReportService service;
	private final OperatorAuthorizationService authorizationService;

	public AdminReportController(AdminReportService service, OperatorAuthorizationService authorizationService) {
		this.service = service;
		this.authorizationService = authorizationService;
	}

	@GetMapping
	public ResponseEntity<List<AdminReportResponse>> list(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to,
			@RequestParam(value = "limit", defaultValue = "50") int limit
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.REPORT_READ);
		var page = service.listPage(status, cursor, from, to, limit);
		return ResponseEntity.ok().headers(CursorSupport.headers(page)).body(page.items());
	}

	@GetMapping("/{reportId}")
	public ResponseEntity<AdminReportResponse> get(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long reportId
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.REPORT_READ);
		return ResponseEntity.ok(service.get(reportId));
	}

	@PostMapping("/{reportId}/resolve")
	public ResponseEntity<AdminReportResponse> resolve(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long reportId,
			@RequestBody ReportResolveRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.REPORT_RESOLVE);
		return ResponseEntity.ok(service.resolve(reportId, request, actor, AuditContext.from(servletRequest)));
	}

	@PatchMapping("/{reportId}/assignment")
	public ResponseEntity<AdminReportResponse> assign(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long reportId,
			@RequestBody ReportAssignmentRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.REPORT_RESOLVE);
		return ResponseEntity.ok(service.assign(reportId, request, actor, AuditContext.from(servletRequest)));
	}
}
