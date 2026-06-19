package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InstantCrewParticipantResponse(
		@JsonProperty("user_id") Long userId,
		String nickname,
		@JsonProperty("profile_image_url") String profileImageUrl,
		String status,
		@JsonProperty("joined_at") String joinedAt
) {}
