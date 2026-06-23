package com.neostride.server.admin.service;

import com.neostride.server.admin.dto.AdminReportResponse;
import com.neostride.server.admin.dto.ReportAssignmentRequest;
import com.neostride.server.admin.repository.AdminReportRepository;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminReportServiceTest {
	private final AdminReportRepository repository = mock(AdminReportRepository.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);
	private final AdminReportService service = new AdminReportService(repository, auditLogService);
	private final OperatorPrincipal actor = new OperatorPrincipal(5L, "mod@example.com", "Mod", "MODERATOR", List.of(OperatorPermissions.REPORT_RESOLVE));
	private final AuditContext context = new AuditContext("req-1", "127.0.0.1", "JUnit");

	@Test
	void assignUpdatesAssigneeAndAudits() {
		AdminReportResponse before = report(20L, null);
		AdminReportResponse after = report(20L, 7L);
		when(repository.find(20L)).thenReturn(Optional.of(before));
		when(repository.assign(20L, 7L)).thenReturn(after);

		AdminReportResponse response = service.assign(20L, new ReportAssignmentRequest(7L, "handoff"), actor, context);

		assertThat(response).isEqualTo(after);
		verify(repository).assign(20L, 7L);
		verify(auditLogService).record(eq(5L), eq("report.assign"), eq("admin_report"), eq("20"),
				eq("handoff"), eq("assigned_operator_account_id=null"), eq("assigned_operator_account_id=7"), eq(context));
	}

	@Test
	void assignRequiresReason() {
		assertThatThrownBy(() -> service.assign(20L, new ReportAssignmentRequest(null, " "), actor, context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reason");
		verify(repository, never()).assign(eq(20L), eq(null));
	}

	private AdminReportResponse report(long id, Long assignedOperatorAccountId) {
		LocalDateTime now = LocalDateTime.now();
		return new AdminReportResponse(id, 1L, 2L, "FEED", "33", "ABUSE", "PENDING",
				"reported", null, assignedOperatorAccountId, null, now, now);
	}
}
