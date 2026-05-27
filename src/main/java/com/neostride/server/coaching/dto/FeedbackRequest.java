package com.neostride.server.coaching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "AI 피드백 요청")
public record FeedbackRequest(
		@JsonProperty("plan_day_id") Long planDayId,
		@JsonProperty("actual_distance_km") BigDecimal actualDistanceKm,
		@JsonProperty("actual_time_sec") Integer actualTimeSec,
		@JsonProperty("actual_pace_sec_per_km") Integer actualPaceSecPerKm
) {
}
