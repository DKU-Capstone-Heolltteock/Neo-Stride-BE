package com.neostride.server.devtools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BugReportStatusRequest(
		@JsonProperty("status")
		String status,
		@JsonProperty("reason")
		String reason
) {}
