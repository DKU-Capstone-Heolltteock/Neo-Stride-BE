package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InstantCrewApplicationResponse(
		@JsonProperty("instant_crew_id") Long instantCrewId,
		@JsonProperty("user_id") Long userId,
		String status,
		String message
) {}
