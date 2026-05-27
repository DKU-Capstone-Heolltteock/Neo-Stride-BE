package com.neostride.server.coaching.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AiCoachingPlanDay(
		@JsonProperty("plan_date") LocalDate planDate,
		@JsonProperty("day_distance_km") BigDecimal dayDistanceKm,
		@JsonProperty("day_pace_sec_per_km") Integer dayPaceSecPerKm,
		@JsonProperty("description") String description
) {
}
