package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReportResolveRequest(
		@JsonProperty("status")
		String status,
		@JsonProperty("resolution")
		String resolution,
		@JsonProperty("reason")
		String reason
) {}
