package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BroadcastRequest(
		@JsonProperty("title")
		String title,
		@JsonProperty("message")
		String message,
		@JsonProperty("target_type")
		String targetType,
		@JsonProperty("target_user_id")
		Long targetUserId,
		@JsonProperty("reason")
		String reason
) {}
