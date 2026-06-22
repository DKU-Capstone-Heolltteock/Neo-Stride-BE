package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record AdminReportResponse(
		@JsonProperty("report_id")
		long reportId,
		@JsonProperty("reporter_user_id")
		Long reporterUserId,
		@JsonProperty("target_user_id")
		Long targetUserId,
		@JsonProperty("target_type")
		String targetType,
		@JsonProperty("target_id")
		String targetId,
		@JsonProperty("category")
		String category,
		@JsonProperty("status")
		String status,
		@JsonProperty("reason")
		String reason,
		@JsonProperty("resolution")
		String resolution,
		@JsonProperty("assigned_operator_account_id")
		Long assignedOperatorAccountId,
		@JsonProperty("resolved_at")
		LocalDateTime resolvedAt,
		@JsonProperty("created_at")
		LocalDateTime createdAt,
		@JsonProperty("updated_at")
		LocalDateTime updatedAt
) {}
