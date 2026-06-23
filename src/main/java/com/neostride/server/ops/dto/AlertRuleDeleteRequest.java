package com.neostride.server.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AlertRuleDeleteRequest(
		@JsonProperty("reason")
		String reason
) {}
