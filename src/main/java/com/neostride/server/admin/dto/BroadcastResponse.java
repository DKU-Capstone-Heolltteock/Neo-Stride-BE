package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record BroadcastResponse(
		@JsonProperty("broadcast_id")
		long broadcastId,
		@JsonProperty("sender_operator_account_id")
		Long senderOperatorAccountId,
		@JsonProperty("title")
		String title,
		@JsonProperty("message")
		String message,
		@JsonProperty("target_type")
		String targetType,
		@JsonProperty("target_user_id")
		Long targetUserId,
		@JsonProperty("recipient_count")
		int recipientCount,
		@JsonProperty("status")
		String status,
		@JsonProperty("discord_status")
		String discordStatus,
		@JsonProperty("discord_error")
		String discordError,
		@JsonProperty("reason")
		String reason,
		@JsonProperty("created_at")
		LocalDateTime createdAt
) {}
