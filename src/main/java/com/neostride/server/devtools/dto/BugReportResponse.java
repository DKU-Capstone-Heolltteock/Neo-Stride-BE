package com.neostride.server.devtools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record BugReportResponse(
		@JsonProperty("bug_report_id")
		long bugReportId,
		@JsonProperty("reporter_user_id")
		Long reporterUserId,
		@JsonProperty("title")
		String title,
		@JsonProperty("description")
		String description,
		@JsonProperty("status")
		String status,
		@JsonProperty("severity")
		String severity,
		@JsonProperty("app_version")
		String appVersion,
		@JsonProperty("device_model")
		String deviceModel,
		@JsonProperty("created_at")
		LocalDateTime createdAt,
		@JsonProperty("updated_at")
		LocalDateTime updatedAt
) {}
