package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewJoinResponse(
		@JsonProperty("crew_id") Long crewId,
		@JsonProperty("user_id") Long userId,
		String role,
		String status,
		String message
) {}
