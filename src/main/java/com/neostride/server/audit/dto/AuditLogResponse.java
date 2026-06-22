package com.neostride.server.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record AuditLogResponse(
		@JsonProperty("operator_audit_log_id")
		long operatorAuditLogId,
		@JsonProperty("actor_operator_account_id")
		Long actorOperatorAccountId,
		@JsonProperty("action")
		String action,
		@JsonProperty("target_type")
		String targetType,
		@JsonProperty("target_id")
		String targetId,
		@JsonProperty("reason")
		String reason,
		@JsonProperty("before_summary")
		String beforeSummary,
		@JsonProperty("after_summary")
		String afterSummary,
		@JsonProperty("request_id")
		String requestId,
		@JsonProperty("ip_address")
		String ipAddress,
		@JsonProperty("user_agent")
		String userAgent,
		@JsonProperty("created_at")
		LocalDateTime createdAt
) {}
