package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccountRestoreRequest(
		@JsonProperty("reason")
		String reason
) {}
