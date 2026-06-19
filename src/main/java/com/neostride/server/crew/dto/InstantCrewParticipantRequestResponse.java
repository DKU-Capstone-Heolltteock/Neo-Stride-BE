package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InstantCrewParticipantRequestResponse(
		@JsonProperty("instant_crew_id") Long instantCrewId,
		@JsonProperty("user_id") Long userId,
		String nickname,
		@JsonProperty("profile_image_url") String profileImageUrl,
		String status,
		@JsonProperty("requested_at") String requestedAt
) {}
