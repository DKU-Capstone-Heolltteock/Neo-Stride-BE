package com.neostride.server.devtools.service;

import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.devtools.dto.ErrorEventResponse;
import com.neostride.server.devtools.repository.BugReportRepository;
import com.neostride.server.devtools.repository.ErrorEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevtoolsServiceTest {
	private final BugReportRepository bugReportRepository = mock(BugReportRepository.class);
	private final ErrorEventRepository errorEventRepository = mock(ErrorEventRepository.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);
	private final DevtoolsService service = new DevtoolsService(bugReportRepository, errorEventRepository, auditLogService);

	@Test
	void searchLogsRecordsAuditWithoutRawQuery() {
		OperatorPrincipal actor = new OperatorPrincipal(9L, "dev@example.com", "Dev", "DEVELOPER", List.of(OperatorPermissions.LOGS_READ));
		AuditContext context = new AuditContext("req-1", "127.0.0.1", "JUnit");
		ErrorEventResponse event = new ErrorEventResponse(1L, "GET", "/api/test", 500, "RuntimeException", "redacted", "req-1", LocalDateTime.now());
		when(errorEventRepository.recent(eq("token=abc"), eq(500), isNull(), isNull(), isNull(), eq(21))).thenReturn(List.of(event));

		List<ErrorEventResponse> events = service.searchLogs("token=abc", 500, 20, actor, context);

		assertThat(events).containsExactly(event);
		verify(auditLogService).record(eq(9L), eq("log.search"), eq("server_error_event"), isNull(),
				argThat(reason -> reason.contains("query_present=true")
						&& reason.contains("query_length=9")
						&& reason.contains("status_code=500")
						&& !reason.contains("token=abc")),
				isNull(), eq("returned_count=1"), eq(context));
	}

	@Test
	void recentErrorsRecordsAudit() {
		OperatorPrincipal actor = new OperatorPrincipal(9L, "dev@example.com", "Dev", "DEVELOPER", List.of(OperatorPermissions.LOGS_READ));
		AuditContext context = new AuditContext("req-1", "127.0.0.1", "JUnit");
		when(errorEventRepository.recent(isNull(), isNull(), isNull(), isNull(), isNull(), eq(201))).thenReturn(List.of());

		service.recentErrors(500, actor, context);

		verify(auditLogService).record(eq(9L), eq("log.recent-errors"), eq("server_error_event"), isNull(),
				eq("recent_errors; limit=200"), isNull(), eq("returned_count=0"), eq(context));
	}
	@Test
	void getErrorEventRecordsAudit() {
		OperatorPrincipal actor = new OperatorPrincipal(9L, "dev@example.com", "Dev", "DEVELOPER", List.of(OperatorPermissions.LOGS_READ));
		AuditContext context = new AuditContext("req-1", "127.0.0.1", "JUnit");
		ErrorEventResponse event = new ErrorEventResponse(3L, "POST", "/api/fail", 500, "IllegalStateException", "summary", "req-3", LocalDateTime.now());
		when(errorEventRepository.find(3L)).thenReturn(Optional.of(event));

		ErrorEventResponse response = service.getErrorEvent(3L, actor, context);

		assertThat(response).isEqualTo(event);
		verify(auditLogService).record(eq(9L), eq("log.get"), eq("server_error_event"), eq("3"),
				eq("get error event detail"), isNull(), eq("IllegalStateException"), eq(context));
	}

}
