package com.neostride.server.ops.service;

import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.auth.api.UserAdministrationPort;
import com.neostride.server.ops.dto.AlertRuleDeleteRequest;
import com.neostride.server.ops.dto.AlertRuleEnabledRequest;
import com.neostride.server.ops.dto.AlertRuleRequest;
import com.neostride.server.ops.dto.AlertRuleResponse;
import com.neostride.server.ops.dto.AlertRuleUpdateRequest;
import com.neostride.server.ops.dto.ApiTrafficMetricResponse;
import com.neostride.server.ops.dto.ErrorMetricResponse;
import com.neostride.server.ops.dto.UsageMetricResponse;
import com.neostride.server.ops.repository.AlertRuleRepository;
import com.neostride.server.ops.repository.OpsMetricsRepository;
import com.neostride.server.platform.web.CursorSupport;
import com.neostride.server.platform.web.CursorSupport.CursorPage;
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
		return alertRulesPage(null, null, null, 50).items();
	}

	public CursorPage<AlertRuleResponse> alertRulesPage(String cursor, String from, String to, int limit) {
		var rows = alertRuleRepository.list(
				CursorSupport.decode(cursor),
				CursorSupport.parseDateTime(from, "from"),
				CursorSupport.parseDateTime(to, "to"),
				CursorSupport.fetchLimit(limit)
		);
		return CursorSupport.page(rows, limit, AlertRuleResponse::createdAt, AlertRuleResponse::alertRuleId);
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
	public AlertRuleResponse updateAlertRule(long ruleId, AlertRuleUpdateRequest request, OperatorPrincipal actor, AuditContext context) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		String reason = requireReason(request.reason());
		AlertRuleResponse before = getAlertRule(ruleId);
		String name = request.name() == null ? before.name() : requireText(request.name(), "name");
		String metricType = request.metricType() == null ? before.metricType() : normalizeMetricType(request.metricType());
		double thresholdValue = request.thresholdValue() == null ? before.thresholdValue() : requirePositive(request.thresholdValue(), "threshold_value");
		int windowMinutes = request.windowMinutes() == null ? before.windowMinutes() : requirePositiveInt(request.windowMinutes(), "window_minutes");
		if (name.equals(before.name()) && metricType.equals(before.metricType())
				&& thresholdValue == before.thresholdValue() && windowMinutes == before.windowMinutes()) {
			throw new IllegalArgumentException("수정할 알림 정책 정보가 없습니다.");
		}
		AlertRuleResponse after = alertRuleRepository.update(ruleId, name, metricType, thresholdValue, windowMinutes);
		auditLogService.record(actor.operatorAccountId(), "alert-rule.update", "operator_alert_rule",
				String.valueOf(ruleId), reason, alertSummary(before), alertSummary(after), context);
		return after;
	}

	@Transactional
	public AlertRuleResponse updateAlertRuleEnabled(long ruleId, AlertRuleEnabledRequest request, OperatorPrincipal actor, AuditContext context) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		String reason = requireReason(request.reason());
		if (request.enabled() == null) {
			throw new IllegalArgumentException("enabled는 필수입니다.");
		}
		AlertRuleResponse before = getAlertRule(ruleId);
		AlertRuleResponse after = alertRuleRepository.updateEnabled(ruleId, request.enabled());
		auditLogService.record(actor.operatorAccountId(), "alert-rule.enabled-update", "operator_alert_rule",
				String.valueOf(ruleId), reason, "enabled=" + before.enabled(), "enabled=" + after.enabled(), context);
		return after;
	}

	@Transactional
	public void deleteAlertRule(long ruleId, AlertRuleDeleteRequest request, OperatorPrincipal actor, AuditContext context) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		String reason = requireReason(request.reason());
		AlertRuleResponse before = getAlertRule(ruleId);
		alertRuleRepository.delete(ruleId);
		auditLogService.record(actor.operatorAccountId(), "alert-rule.delete", "operator_alert_rule",
				String.valueOf(ruleId), reason, alertSummary(before), null, context);
	}

	@Transactional
	public AlertRuleResponse testAlertRule(long ruleId, OperatorPrincipal actor, AuditContext context) {
		AlertRuleResponse rule = getAlertRule(ruleId);
		DiscordWebhookClient.DiscordWebhookResult result = discordWebhookClient.send(
				"[Neo-Stride 알림 정책 테스트] " + rule.name() + " / " + rule.metricType()
		);
		AlertRuleResponse updated = alertRuleRepository.updateTestResult(ruleId, result.status(), result.error());
		auditLogService.record(actor.operatorAccountId(), "alert-rule.test", "operator_alert_rule",
				String.valueOf(ruleId), "test discord alert rule", rule.discordStatus(), updated.discordStatus(), context);
		return updated;
	}

	private AlertRuleResponse getAlertRule(long ruleId) {
		return alertRuleRepository.find(ruleId)
				.orElseThrow(() -> new IllegalArgumentException("알림 정책을 찾을 수 없습니다."));
	}

	private void validateAlertRule(AlertRuleRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		requireText(request.name(), "name");
		normalizeMetricType(request.metricType());
		requirePositive(request.thresholdValue(), "threshold_value");
		requirePositiveInt(request.windowMinutes(), "window_minutes");
		requireReason(request.reason());
	}

	private String requireReason(String reason) {
		return requireText(reason, "reason");
	}

	private String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + "은 필수입니다.");
		}
		return value.trim();
	}

	private String normalizeMetricType(String metricType) {
		String normalized = requireText(metricType, "metric_type").toUpperCase();
		if (!List.of("API_ERROR_RATE", "API_TRAFFIC", "SERVER_ERROR_COUNT").contains(normalized)) {
			throw new IllegalArgumentException("metric_type 값이 올바르지 않습니다.");
		}
		return normalized;
	}

	private double requirePositive(Double value, String fieldName) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(fieldName + "는 0보다 커야 합니다.");
		}
		return value;
	}

	private int requirePositiveInt(Integer value, String fieldName) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(fieldName + "는 1 이상이어야 합니다.");
		}
		return value;
	}

	private String alertSummary(AlertRuleResponse rule) {
		return "name=" + rule.name()
				+ "; metric_type=" + rule.metricType()
				+ "; threshold_value=" + rule.thresholdValue()
				+ "; window_minutes=" + rule.windowMinutes()
				+ "; enabled=" + rule.enabled();
	}
}
