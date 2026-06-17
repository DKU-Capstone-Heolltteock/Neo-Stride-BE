package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewMemberResponse(
		@JsonProperty("crew_id") Long crewId,
		@JsonProperty("user_id") Long userId,
		String nickname,
		@JsonProperty("profile_image_url") String profileImageUrl,
		String role,
		String status,
		@JsonProperty("joined_at") String joinedAt
) {}
