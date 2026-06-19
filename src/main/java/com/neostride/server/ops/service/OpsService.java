package com.neostride.server.ops.service;

import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.auth.api.UserAdministrationPort;
import com.neostride.server.ops.dto.AlertRuleRequest;
import com.neostride.server.ops.dto.AlertRuleResponse;
import com.neostride.server.ops.dto.ApiTrafficMetricResponse;
import com.neostride.server.ops.dto.ErrorMetricResponse;
import com.neostride.server.ops.dto.UsageMetricResponse;
import com.neostride.server.ops.repository.AlertRuleRepository;
import com.neostride.server.ops.repository.OpsMetricsRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpsService {
	private final OpsMetricsRepository metricsRepository;
	private final AlertRuleRepository alertRuleRepository;
	private final UserAdministrationPort userAdministrationPort;
	private final DiscordWebhookClient discordWebhookClient;
	private final AuditLogService auditLogService;

	public OpsService(
			OpsMetricsRepository metricsRepository,
			AlertRuleRepository alertRuleRepository,
			UserAdministrationPort userAdministrationPort,
			DiscordWebhookClient discordWebhookClient,
			AuditLogService auditLogService
	) {
		this.metricsRepository = metricsRepository;
		this.alertRuleRepository = alertRuleRepository;
		this.userAdministrationPort = userAdministrationPort;
		this.discordWebhookClient = discordWebhookClient;
		this.auditLogService = auditLogService;
	}

	public void recordRequest(String method, String path, int statusCode, long durationMs) {
		metricsRepository.record(method, path, statusCode, durationMs);
	}

	public List<ApiTrafficMetricResponse> apiTraffic(int hours, int limit) {
		return metricsRepository.apiTraffic(hours, limit);
	}

	public List<ErrorMetricResponse> errors(int hours) {
		return metricsRepository.errors(hours);
	}

	public UsageMetricResponse usage() {
		return new UsageMetricResponse(
				userAdministrationPort.countAccounts(null),
				userAdministrationPort.countAccounts("ACTIVE"),
				userAdministrationPort.countAccounts("SUSPENDED"),
				metricsRepository.countRequestsLast24h(),
				metricsRepository.countErrorsLast24h()
		);
	}

	public List<AlertRuleResponse> alertRules() {
		return alertRuleRepository.list();
	}

	@Transactional
	public AlertRuleResponse createAlertRule(AlertRuleRequest request, OperatorPrincipal actor, AuditContext context) {
		validateAlertRule(request);
		AlertRuleResponse response = alertRuleRepository.create(request, actor.operatorAccountId());
		auditLogService.record(actor.operatorAccountId(), "alert-rule.create", "operator_alert_rule",
				String.valueOf(response.alertRuleId()), request.reason(), null, response.metricType(), context);
		return response;
	}

	@Transactional
	public AlertRuleResponse testAlertRule(long ruleId, OperatorPrincipal actor, AuditContext context) {
		AlertRuleResponse rule = alertRuleRepository.find(ruleId)
				.orElseThrow(() -> new IllegalArgumentException("알림 정책을 찾을 수 없습니다."));
		DiscordWebhookClient.DiscordWebhookResult result = discordWebhookClient.send(
				"[Neo-Stride 알림 정책 테스트] " + rule.name() + " / " + rule.metricType()
		);
		AlertRuleResponse updated = alertRuleRepository.updateTestResult(ruleId, result.status(), result.error());
		auditLogService.record(actor.operatorAccountId(), "alert-rule.test", "operator_alert_rule",
				String.valueOf(ruleId), "test discord alert rule", rule.discordStatus(), updated.discordStatus(), context);
		return updated;
	}

	private void validateAlertRule(AlertRuleRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		if (request.name() == null || request.name().isBlank()) {
			throw new IllegalArgumentException("name은 필수입니다.");
		}
		if (request.thresholdValue() == null || request.thresholdValue() <= 0) {
			throw new IllegalArgumentException("threshold_value는 0보다 커야 합니다.");
		}
		if (request.windowMinutes() == null || request.windowMinutes() <= 0) {
			throw new IllegalArgumentException("window_minutes는 1 이상이어야 합니다.");
		}
		if (request.reason() == null || request.reason().isBlank()) {
			throw new IllegalArgumentException("reason은 필수입니다.");
		}
	}
}
