package com.neostride.server.ops.controller;

import com.neostride.server.admin.security.OperatorAuthorizationService;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.ops.dto.AlertRuleRequest;
import com.neostride.server.ops.dto.AlertRuleResponse;
import com.neostride.server.ops.dto.ApiTrafficMetricResponse;
import com.neostride.server.ops.dto.ErrorMetricResponse;
import com.neostride.server.ops.dto.UsageMetricResponse;
import com.neostride.server.ops.service.OpsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
public class OpsController {
	private final OpsService service;
	private final OperatorAuthorizationService authorizationService;

	public OpsController(OpsService service, OperatorAuthorizationService authorizationService) {
		this.service = service;
		this.authorizationService = authorizationService;
	}

	@GetMapping("/metrics/api-traffic")
	public ResponseEntity<List<ApiTrafficMetricResponse>> apiTraffic(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "hours", defaultValue = "24") int hours,
			@RequestParam(value = "limit", defaultValue = "50") int limit
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.METRICS_READ);
		return ResponseEntity.ok(service.apiTraffic(hours, limit));
	}

	@GetMapping("/metrics/errors")
	public ResponseEntity<List<ErrorMetricResponse>> errors(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "hours", defaultValue = "24") int hours
	) {
		authorizationService.requirePermission(authorization, OperatorPermissions.METRICS_READ);
		return ResponseEntity.ok(service.errors(hours));
	}

	@GetMapping("/metrics/usage")
	public ResponseEntity<UsageMetricResponse> usage(@RequestHeader(value = "Authorization", required = false) String authorization) {
		authorizationService.requirePermission(authorization, OperatorPermissions.METRICS_READ);
		return ResponseEntity.ok(service.usage());
	}

	@GetMapping("/alert-rules")
	public ResponseEntity<List<AlertRuleResponse>> alertRules(@RequestHeader(value = "Authorization", required = false) String authorization) {
		authorizationService.requireAnyPermission(authorization, OperatorPermissions.METRICS_READ, OperatorPermissions.ALERT_POLICY_WRITE);
		return ResponseEntity.ok(service.alertRules());
	}

	@PostMapping("/alert-rules")
	public ResponseEntity<AlertRuleResponse> createAlertRule(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody AlertRuleRequest request,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.ALERT_POLICY_WRITE);
		return ResponseEntity.ok(service.createAlertRule(request, actor, AuditContext.from(servletRequest)));
	}

	@PostMapping("/alert-rules/{ruleId}/test")
	public ResponseEntity<AlertRuleResponse> testAlertRule(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable long ruleId,
			HttpServletRequest servletRequest
	) {
		var actor = authorizationService.requirePermission(authorization, OperatorPermissions.ALERT_POLICY_WRITE);
		return ResponseEntity.ok(service.testAlertRule(ruleId, actor, AuditContext.from(servletRequest)));
	}
}
