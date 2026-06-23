package com.neostride.server.devtools.service;

import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.devtools.dto.BugReportResponse;
import com.neostride.server.devtools.dto.BugReportStatusRequest;
import com.neostride.server.devtools.dto.ErrorEventResponse;
import com.neostride.server.devtools.repository.BugReportRepository;
import com.neostride.server.devtools.repository.ErrorEventRepository;
import com.neostride.server.platform.web.CursorSupport;
import com.neostride.server.platform.web.CursorSupport.CursorPage;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevtoolsService {
	private final BugReportRepository bugReportRepository;
	private final ErrorEventRepository errorEventRepository;
	private final AuditLogService auditLogService;

	public DevtoolsService(BugReportRepository bugReportRepository, ErrorEventRepository errorEventRepository, AuditLogService auditLogService) {
		this.bugReportRepository = bugReportRepository;
		this.errorEventRepository = errorEventRepository;
		this.auditLogService = auditLogService;
	}

	public List<BugReportResponse> listBugReports(String status, int limit) {
		return listBugReportsPage(status, null, null, null, limit).items();
	}

	public CursorPage<BugReportResponse> listBugReportsPage(String status, String cursor, String from, String to, int limit) {
		var rows = bugReportRepository.list(
				status,
				CursorSupport.decode(cursor),
				CursorSupport.parseDateTime(from, "from"),
				CursorSupport.parseDateTime(to, "to"),
				CursorSupport.fetchLimit(limit)
		);
		return CursorSupport.page(rows, limit, BugReportResponse::createdAt, BugReportResponse::bugReportId);
	}

	public BugReportResponse getBugReport(long bugReportId) {
		return bugReportRepository.find(bugReportId)
				.orElseThrow(() -> new IllegalArgumentException("버그 리포트를 찾을 수 없습니다."));
	}

	@Transactional
	public BugReportResponse updateBugReportStatus(long bugReportId, BugReportStatusRequest request, OperatorPrincipal actor, AuditContext context) {
		if (request == null || request.status() == null || request.status().isBlank()) {
			throw new IllegalArgumentException("status는 필수입니다.");
		}
		BugReportResponse before = getBugReport(bugReportId);
		BugReportResponse after = bugReportRepository.updateStatus(bugReportId, request.status());
		auditLogService.record(actor.operatorAccountId(), "bug-report.status-update", "bug_report", String.valueOf(bugReportId),
				request.reason(), before.status(), after.status(), context);
		return after;
	}

	@Transactional
	public List<ErrorEventResponse> searchLogs(String query, Integer statusCode, int limit, OperatorPrincipal actor, AuditContext context) {
		return searchLogsPage(query, statusCode, null, null, null, limit, actor, context).items();
	}

	@Transactional
	public CursorPage<ErrorEventResponse> searchLogsPage(String query, Integer statusCode, String cursor, String from, String to, int limit, OperatorPrincipal actor, AuditContext context) {
		List<ErrorEventResponse> rows = errorEventRepository.recent(
				query,
				statusCode,
				CursorSupport.decode(cursor),
				CursorSupport.parseDateTime(from, "from"),
				CursorSupport.parseDateTime(to, "to"),
				CursorSupport.fetchLimit(limit)
		);
		CursorPage<ErrorEventResponse> page = CursorSupport.page(rows, limit, ErrorEventResponse::createdAt, ErrorEventResponse::errorEventId);
		auditLogService.record(actor.operatorAccountId(), "log.search", "server_error_event", null,
				logSearchReason(query, statusCode, limit), null, returnedCount(page.items()), context);
		return page;
	}

	@Transactional
	public List<ErrorEventResponse> recentErrors(int limit, OperatorPrincipal actor, AuditContext context) {
		return recentErrorsPage(null, null, null, limit, actor, context).items();
	}

	@Transactional
	public CursorPage<ErrorEventResponse> recentErrorsPage(String cursor, String from, String to, int limit, OperatorPrincipal actor, AuditContext context) {
		List<ErrorEventResponse> rows = errorEventRepository.recent(
				null,
				null,
				CursorSupport.decode(cursor),
				CursorSupport.parseDateTime(from, "from"),
				CursorSupport.parseDateTime(to, "to"),
				CursorSupport.fetchLimit(limit)
		);
		CursorPage<ErrorEventResponse> page = CursorSupport.page(rows, limit, ErrorEventResponse::createdAt, ErrorEventResponse::errorEventId);
		auditLogService.record(actor.operatorAccountId(), "log.recent-errors", "server_error_event", null,
				"recent_errors; limit=" + CursorSupport.cappedLimit(limit), null, returnedCount(page.items()), context);
		return page;
	}

	@Transactional
	public ErrorEventResponse getErrorEvent(long errorEventId, OperatorPrincipal actor, AuditContext context) {
		ErrorEventResponse response = errorEventRepository.find(errorEventId)
				.orElseThrow(() -> new IllegalArgumentException("에러 로그를 찾을 수 없습니다."));
		auditLogService.record(actor.operatorAccountId(), "log.get", "server_error_event", String.valueOf(errorEventId),
				"get error event detail", null, response.errorType(), context);
		return response;
	}

	private String logSearchReason(String query, Integer statusCode, int limit) {
		boolean queryPresent = query != null && !query.isBlank();
		int queryLength = queryPresent ? query.trim().length() : 0;
		String statusSummary = statusCode == null ? "any" : String.valueOf(statusCode);
		return "query_present=" + queryPresent
				+ "; query_length=" + queryLength
				+ "; status_code=" + statusSummary
				+ "; limit=" + CursorSupport.cappedLimit(limit);
	}

	private String returnedCount(List<ErrorEventResponse> events) {
		return "returned_count=" + events.size();
	}
}
