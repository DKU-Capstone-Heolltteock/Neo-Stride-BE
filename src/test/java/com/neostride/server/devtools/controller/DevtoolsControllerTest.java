package com.neostride.server.devtools.controller;

import com.neostride.server.admin.security.OperatorAuthorizationService;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.devtools.dto.BugReportResponse;
import com.neostride.server.devtools.dto.BugReportStatusRequest;
import com.neostride.server.devtools.service.DevtoolsService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevtoolsControllerTest {
	private final DevtoolsService service = mock(DevtoolsService.class);
	private final OperatorAuthorizationService authorizationService = mock(OperatorAuthorizationService.class);
	private final DevtoolsController controller = new DevtoolsController(service, authorizationService);

	@Test
	void updateBugReportStatusRequiresBugReportWritePermission() {
		OperatorPrincipal actor = new OperatorPrincipal(7L, "dev@example.com", "Dev", "DEVELOPER", List.of(OperatorPermissions.BUG_REPORT_WRITE));
		BugReportStatusRequest request = new BugReportStatusRequest("RESOLVED", "fixed");
		BugReportResponse response = new BugReportResponse(10L, 1L, "title", "desc", "RESOLVED", "HIGH", "1.0", "Pixel", LocalDateTime.now(), LocalDateTime.now());
		MockHttpServletRequest servletRequest = new MockHttpServletRequest();

		when(authorizationService.requirePermission("Bearer token", OperatorPermissions.BUG_REPORT_WRITE)).thenReturn(actor);
		when(service.updateBugReportStatus(eq(10L), eq(request), eq(actor), any(AuditContext.class))).thenReturn(response);

		controller.updateBugReportStatus("Bearer token", 10L, request, servletRequest);

		verify(authorizationService).requirePermission("Bearer token", OperatorPermissions.BUG_REPORT_WRITE);
		verify(service).updateBugReportStatus(eq(10L), eq(request), eq(actor), any(AuditContext.class));
	}
}
