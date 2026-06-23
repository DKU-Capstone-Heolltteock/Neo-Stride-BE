package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OperatorUpdateRequest(
		@JsonProperty("email")
		String email,
		@JsonProperty("name")
		String name,
		@JsonProperty("role")
		String role,
		@JsonProperty("reason")
		String reason
) {}
