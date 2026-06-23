package com.neostride.server.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AlertRuleEnabledRequest(
		@JsonProperty("enabled")
		Boolean enabled,
		@JsonProperty("reason")
		String reason
) {}
