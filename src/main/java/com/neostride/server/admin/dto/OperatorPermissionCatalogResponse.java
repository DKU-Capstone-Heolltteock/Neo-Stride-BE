package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record OperatorPermissionCatalogResponse(
		@JsonProperty("roles")
		List<String> roles,
		@JsonProperty("permissions")
		List<String> permissions,
		@JsonProperty("role_defaults")
		Map<String, List<String>> roleDefaults
) {}
