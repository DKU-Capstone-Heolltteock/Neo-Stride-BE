package com.neostride.server.admin.service;

import com.neostride.server.admin.dto.AdminReportResponse;
import com.neostride.server.admin.dto.ReportResolveRequest;
import com.neostride.server.admin.repository.AdminReportRepository;
import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminReportService {
	private final AdminReportRepository repository;
	private final AuditLogService auditLogService;

	public AdminReportService(AdminReportRepository repository, AuditLogService auditLogService) {
		this.repository = repository;
		this.auditLogService = auditLogService;
	}

	public List<AdminReportResponse> list(String status, int limit) {
		return repository.list(status, limit);
	}

	public AdminReportResponse get(long reportId) {
		return repository.find(reportId).orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없습니다."));
	}

	@Transactional
	public AdminReportResponse resolve(long reportId, ReportResolveRequest request, OperatorPrincipal actor, AuditContext context) {
		if (request == null || request.reason() == null || request.reason().isBlank()) {
			throw new IllegalArgumentException("reason은 필수입니다.");
		}
		AdminReportResponse before = get(reportId);
		AdminReportResponse after = repository.resolve(reportId, request.status(), request.resolution(), actor.operatorAccountId());
		auditLogService.record(actor.operatorAccountId(), "report.resolve", "admin_report", String.valueOf(reportId),
				request.reason().trim(), before.status(), after.status(), context);
		return after;
	}
}
