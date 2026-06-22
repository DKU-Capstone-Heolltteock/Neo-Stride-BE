package com.neostride.server.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record AlertRuleResponse(
		@JsonProperty("alert_rule_id")
		long alertRuleId,
		@JsonProperty("name")
		String name,
		@JsonProperty("metric_type")
		String metricType,
		@JsonProperty("threshold_value")
		double thresholdValue,
		@JsonProperty("window_minutes")
		int windowMinutes,
		@JsonProperty("channel")
		String channel,
		@JsonProperty("enabled")
		boolean enabled,
		@JsonProperty("discord_status")
		String discordStatus,
		@JsonProperty("discord_error")
		String discordError,
		@JsonProperty("last_tested_at")
		LocalDateTime lastTestedAt,
		@JsonProperty("created_by_operator_account_id")
		Long createdByOperatorAccountId,
		@JsonProperty("created_at")
		LocalDateTime createdAt,
		@JsonProperty("updated_at")
		LocalDateTime updatedAt
) {}
