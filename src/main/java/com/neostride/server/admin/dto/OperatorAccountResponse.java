package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record OperatorAccountResponse(
		@JsonProperty("operator_account_id")
		long operatorAccountId,
		@JsonProperty("email")
		String email,
		@JsonProperty("name")
		String name,
		@JsonProperty("role")
		String role,
		@JsonProperty("status")
		String status,
		@JsonProperty("role_permissions")
		List<String> rolePermissions,
		@JsonProperty("explicit_permissions")
		List<String> explicitPermissions,
		@JsonProperty("permissions")
		List<String> permissions,
		@JsonProperty("last_login_at")
		LocalDateTime lastLoginAt,
		@JsonProperty("created_at")
		LocalDateTime createdAt,
		@JsonProperty("updated_at")
		LocalDateTime updatedAt
) {}
