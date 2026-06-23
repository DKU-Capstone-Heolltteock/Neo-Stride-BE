package com.neostride.server.ops.service;

import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.auth.api.UserAdministrationPort;
import com.neostride.server.ops.dto.AlertRuleDeleteRequest;
import com.neostride.server.ops.dto.AlertRuleEnabledRequest;
import com.neostride.server.ops.dto.AlertRuleResponse;
import com.neostride.server.ops.dto.AlertRuleUpdateRequest;
import com.neostride.server.ops.repository.AlertRuleRepository;
import com.neostride.server.ops.repository.OpsMetricsRepository;
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

class OpsServiceTest {
	private final OpsMetricsRepository metricsRepository = mock(OpsMetricsRepository.class);
	private final AlertRuleRepository alertRuleRepository = mock(AlertRuleRepository.class);
	private final UserAdministrationPort userAdministrationPort = mock(UserAdministrationPort.class);
	private final DiscordWebhookClient discordWebhookClient = mock(DiscordWebhookClient.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);
	private final OpsService service = new OpsService(metricsRepository, alertRuleRepository, userAdministrationPort, discordWebhookClient, auditLogService);
	private final OperatorPrincipal actor = new OperatorPrincipal(6L, "ops@example.com", "Ops", "DEVELOPER", List.of(OperatorPermissions.ALERT_POLICY_WRITE));
	private final AuditContext context = new AuditContext("req-1", "127.0.0.1", "JUnit");

	@Test
	void updateAlertRuleAuditsBeforeAndAfter() {
		AlertRuleResponse before = rule(10L, "Errors", "API_ERROR_RATE", 5.0, 10, true);
		AlertRuleResponse after = rule(10L, "Traffic", "API_TRAFFIC", 100.0, 15, true);
		when(alertRuleRepository.find(10L)).thenReturn(Optional.of(before));
		when(alertRuleRepository.update(10L, "Traffic", "API_TRAFFIC", 100.0, 15)).thenReturn(after);

		AlertRuleResponse response = service.updateAlertRule(10L, new AlertRuleUpdateRequest("Traffic", "api_traffic", 100.0, 15, "tune"), actor, context);

		assertThat(response).isEqualTo(after);
		verify(auditLogService).record(eq(6L), eq("alert-rule.update"), eq("operator_alert_rule"), eq("10"),
				eq("tune"), eq("name=Errors; metric_type=API_ERROR_RATE; threshold_value=5.0; window_minutes=10; enabled=true"),
				eq("name=Traffic; metric_type=API_TRAFFIC; threshold_value=100.0; window_minutes=15; enabled=true"), eq(context));
	}

	@Test
	void updateAlertRuleRequiresReason() {
		assertThatThrownBy(() -> service.updateAlertRule(10L, new AlertRuleUpdateRequest("Traffic", null, null, null, " "), actor, context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reason");
		verify(alertRuleRepository, never()).update(eq(10L), eq("Traffic"), eq("API_TRAFFIC"), eq(100.0), eq(15));
	}

	@Test
	void updateAlertRuleEnabledAudits() {
		AlertRuleResponse before = rule(10L, "Errors", "API_ERROR_RATE", 5.0, 10, true);
		AlertRuleResponse after = rule(10L, "Errors", "API_ERROR_RATE", 5.0, 10, false);
		when(alertRuleRepository.find(10L)).thenReturn(Optional.of(before));
		when(alertRuleRepository.updateEnabled(10L, false)).thenReturn(after);

		AlertRuleResponse response = service.updateAlertRuleEnabled(10L, new AlertRuleEnabledRequest(false, "maintenance"), actor, context);

		assertThat(response).isEqualTo(after);
		verify(auditLogService).record(eq(6L), eq("alert-rule.enabled-update"), eq("operator_alert_rule"), eq("10"),
				eq("maintenance"), eq("enabled=true"), eq("enabled=false"), eq(context));
	}

	@Test
	void deleteAlertRuleAudits() {
		AlertRuleResponse before = rule(10L, "Errors", "API_ERROR_RATE", 5.0, 10, true);
		when(alertRuleRepository.find(10L)).thenReturn(Optional.of(before));

		service.deleteAlertRule(10L, new AlertRuleDeleteRequest("obsolete"), actor, context);

		verify(alertRuleRepository).delete(10L);
		verify(auditLogService).record(eq(6L), eq("alert-rule.delete"), eq("operator_alert_rule"), eq("10"),
				eq("obsolete"), eq("name=Errors; metric_type=API_ERROR_RATE; threshold_value=5.0; window_minutes=10; enabled=true"), eq(null), eq(context));
	}

	private AlertRuleResponse rule(long id, String name, String metricType, double thresholdValue, int windowMinutes, boolean enabled) {
		LocalDateTime now = LocalDateTime.now();
		return new AlertRuleResponse(id, name, metricType, thresholdValue, windowMinutes, "DISCORD", enabled, null, null, null, 6L, now, now);
	}
}
