package com.neostride.server.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorMetricResponse(
		@JsonProperty("status_code")
		int statusCode,
		@JsonProperty("error_count")
		long errorCount
) {}
