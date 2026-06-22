package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OperatorPermissionsUpdateRequest(
		@JsonProperty("permissions")
		List<String> permissions,
		@JsonProperty("reason")
		String reason
) {}
