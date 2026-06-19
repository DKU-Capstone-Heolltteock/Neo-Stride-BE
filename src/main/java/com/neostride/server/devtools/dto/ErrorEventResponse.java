package com.neostride.server.devtools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record ErrorEventResponse(
		@JsonProperty("error_event_id")
		long errorEventId,
		@JsonProperty("method")
		String method,
		@JsonProperty("path")
		String path,
		@JsonProperty("status_code")
		int statusCode,
		@JsonProperty("error_type")
		String errorType,
		@JsonProperty("message_summary")
		String messageSummary,
		@JsonProperty("request_id")
		String requestId,
		@JsonProperty("created_at")
		LocalDateTime createdAt
) {}
