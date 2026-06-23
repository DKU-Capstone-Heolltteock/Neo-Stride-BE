package com.neostride.server.devtools.controller;

import com.neostride.server.admin.security.OperatorAuthorizationService;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.devtools.dto.BugReportResponse;
import com.neostride.server.devtools.dto.BugReportStatusRequest;
import com.neostride.server.devtools.dto.ErrorEventResponse;
import com.neostride.server.devtools.service.DevtoolsService;
import com.neostride.server.platform.web.CursorSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
public class DevtoolsController {
	private final DevtoolsService service;
	private final OperatorAuthorizationService authorizationService;

	public DevtoolsController(DevtoolsService service, OperatorAuthorizationService authorizationService) {
		this.service = service;
		this.authorizationService = authorizationService;
	}

	@GetMapping("/bug-reports")
	public ResponseEntity<List<BugReportResponse>> listBugReports(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to,
			@RequestParam(value = "limit", defaultValue = "50") int limit
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.LOGS_READ);
		var page = service.listBugReportsPage(status, cursor, from, to, limit);
		return ResponseEntity.ok().headers(CursorSupport.headers(page)).body(page.items());
	}

	@GetMapping("/bug-reports/{bugReportId}")
	public ResponseEntity<BugReportResponse> getBugReport(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long bugReportId
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.LOGS_READ);
		return ResponseEntity.ok(service.getBugReport(bugReportId));
	}

	@PatchMapping("/bug-reports/{bugReportId}/status")
	public ResponseEntity<BugReportResponse> updateBugReportStatus(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long bugReportId,
			@RequestBody BugReportStatusRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.BUG_REPORT_WRITE);
		return ResponseEntity.ok(service.updateBugReportStatus(bugReportId, request, actor, AuditContext.from(servletRequest)));
	}

	@GetMapping("/logs/search")
	public ResponseEntity<List<ErrorEventResponse>> searchLogs(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "q", required = false) String query,
			@RequestParam(value = "status_code", required = false) Integer statusCode,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to,
			@RequestParam(value = "limit", defaultValue = "50") int limit,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.LOGS_READ);
		var page = service.searchLogsPage(query, statusCode, cursor, from, to, limit, actor, AuditContext.from(servletRequest));
		return ResponseEntity.ok().headers(CursorSupport.headers(page)).body(page.items());
	}

	@GetMapping("/logs/errors")
	public ResponseEntity<List<ErrorEventResponse>> recentErrors(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to,
			@RequestParam(value = "limit", defaultValue = "50") int limit,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.LOGS_READ);
		var page = service.recentErrorsPage(cursor, from, to, limit, actor, AuditContext.from(servletRequest));
		return ResponseEntity.ok().headers(CursorSupport.headers(page)).body(page.items());
	}

	@GetMapping("/logs/errors/{errorEventId}")
	public ResponseEntity<ErrorEventResponse> getErrorEvent(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long errorEventId,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.LOGS_READ);
		return ResponseEntity.ok(service.getErrorEvent(errorEventId, actor, AuditContext.from(servletRequest)));
	}
}
