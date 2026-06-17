package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewEventResponse(
		@JsonProperty("crew_event_id") Long crewEventId,
		@JsonProperty("crew_id") Long crewId,
		@JsonProperty("host_user_id") Long hostUserId,
		String title,
		String description,
		@JsonProperty("event_type") String eventType,
		String status,
		@JsonProperty("starts_at") String startsAt,
		@JsonProperty("ends_at") String endsAt,
		@JsonProperty("location_label") String locationLabel,
		@JsonProperty("meeting_place") String meetingPlace,
		Integer capacity,
		@JsonProperty("participant_count") Integer participantCount
) {}
