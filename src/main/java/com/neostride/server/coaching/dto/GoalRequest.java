package com.neostride.server.coaching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "코칭 목표 생성 요청")
public record GoalRequest(
		@JsonProperty("user_id") Long userId,
		@JsonProperty("period_type") String periodType,
		@JsonProperty("custom_weeks") Integer customWeeks,
		@JsonProperty("running_days") List<String> runningDays,
		@JsonProperty("goal_distance_km") BigDecimal goalDistanceKm,
		@JsonProperty("goal_pace_min_per_km") BigDecimal goalPaceMinPerKm,
		@JsonProperty("start_date") String startDate
) {
}
