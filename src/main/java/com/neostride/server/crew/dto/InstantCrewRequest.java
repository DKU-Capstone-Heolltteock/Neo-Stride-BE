package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record InstantCrewRequest(
		@JsonProperty("crew_id") @JsonAlias("crewId") Long crewId,
		String title,
		String description,
		String region,
		@JsonProperty("location_label") @JsonAlias("locationLabel") String locationLabel,
		@JsonProperty("meeting_place") @JsonAlias({"meetingPlace", "meeting_place_private", "meetingPlacePrivate"}) String meetingPlace,
		@JsonProperty("starts_at") @JsonAlias("startsAt") LocalDateTime startsAt,
		@JsonProperty("recruit_until") @JsonAlias("recruitUntil") LocalDateTime recruitUntil,
		Integer capacity
) {}
