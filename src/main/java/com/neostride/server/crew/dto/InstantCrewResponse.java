package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InstantCrewResponse(
		@JsonProperty("instant_crew_id") Long instantCrewId,
		@JsonProperty("crew_id") Long crewId,
		@JsonProperty("host_user_id") Long hostUserId,
		String title,
		String description,
		String status,
		String region,
		@JsonProperty("location_label") String locationLabel,
		@JsonProperty("meeting_place") String meetingPlace,
		@JsonProperty("starts_at") String startsAt,
		@JsonProperty("recruit_until") String recruitUntil,
		Integer capacity,
		@JsonProperty("participant_count") Integer participantCount,
		@JsonProperty("viewer_status") String viewerStatus,
		@JsonProperty("created_at") String createdAt
) {}
