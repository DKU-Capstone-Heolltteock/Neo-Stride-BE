package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record CrewEventRequest(
		String title,
		String description,
		@JsonProperty("event_type") @JsonAlias("eventType") String eventType,
		@JsonProperty("starts_at") @JsonAlias("startsAt") LocalDateTime startsAt,
		@JsonProperty("ends_at") @JsonAlias("endsAt") LocalDateTime endsAt,
		@JsonProperty("location_label") @JsonAlias("locationLabel") String locationLabel,
		@JsonProperty("meeting_place") @JsonAlias("meetingPlace") String meetingPlace,
		Integer capacity
) {}
