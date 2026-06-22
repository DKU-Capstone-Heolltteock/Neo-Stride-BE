package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OperatorCreateRequest(
		@JsonProperty("email")
		String email,
		@JsonProperty("password")
		String password,
		@JsonProperty("name")
		String name,
		@JsonProperty("role")
		String role,
		@JsonProperty("permissions")
		List<String> permissions,
		@JsonProperty("reason")
		String reason
) {}
