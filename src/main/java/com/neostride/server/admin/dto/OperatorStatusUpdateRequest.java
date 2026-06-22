package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OperatorStatusUpdateRequest(
		@JsonProperty("status")
		String status,
		@JsonProperty("reason")
		String reason
) {}
