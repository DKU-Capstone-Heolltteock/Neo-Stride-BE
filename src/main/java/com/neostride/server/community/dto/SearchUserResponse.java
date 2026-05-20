package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchUserResponse(
		@JsonProperty("user_id") Long userId,
		String nickname,
		@JsonProperty("profile_image_url") String profileImageUrl,
		@JsonProperty("status_message") String statusMessage,
		@JsonProperty("friend_count") int friendCount,
		@JsonProperty("badge_tier") String badgeTier,
		String status
) {}
