package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FriendResponse(
		@JsonProperty("user_id") Long userId,
		String nickname,
		@JsonProperty("badge_tier") String badgeTier,
		@JsonProperty("friend_count") Integer friendCount,
		@JsonProperty("profile_image_url") String profileImageUrl,
		String status
) {}
