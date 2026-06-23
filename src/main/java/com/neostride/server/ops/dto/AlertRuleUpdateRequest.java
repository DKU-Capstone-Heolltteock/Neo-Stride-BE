package com.neostride.server.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AlertRuleUpdateRequest(
		@JsonProperty("name")
		String name,
		@JsonProperty("metric_type")
		String metricType,
		@JsonProperty("threshold_value")
		Double thresholdValue,
		@JsonProperty("window_minutes")
		Integer windowMinutes,
		@JsonProperty("reason")
		String reason
) {}
