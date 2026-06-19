package com.neostride.server.devtools.service;

import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.devtools.dto.BugReportResponse;
import com.neostride.server.devtools.dto.BugReportStatusRequest;
import com.neostride.server.devtools.dto.ErrorEventResponse;
import com.neostride.server.devtools.repository.BugReportRepository;
import com.neostride.server.devtools.repository.ErrorEventRepository;
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
		return bugReportRepository.list(status, limit);
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

	public List<ErrorEventResponse> searchLogs(String query, Integer statusCode, int limit) {
		return errorEventRepository.recent(query, statusCode, limit);
	}

	public List<ErrorEventResponse> recentErrors(int limit) {
		return errorEventRepository.recent(null, null, limit);
	}
}
