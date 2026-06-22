package com.neostride.server.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiTrafficMetricResponse(
		@JsonProperty("method")
		String method,
		@JsonProperty("path")
		String path,
		@JsonProperty("request_count")
		long requestCount,
		@JsonProperty("error_count")
		long errorCount,
		@JsonProperty("average_duration_ms")
		double averageDurationMs,
		@JsonProperty("max_duration_ms")
		long maxDurationMs
) {}
