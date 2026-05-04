package com.neostride.server.coaching.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "코칭 플랜 일자 응답")
public record PlanDayResponse(
		@JsonProperty("plan_day_id") Long planDayId,
		@JsonProperty("plan_date") String planDate,
		@JsonProperty("day_distance_km") BigDecimal dayDistanceKm,
		@JsonProperty("day_pace_min_per_km") BigDecimal dayPaceMinPerKm,
		@JsonProperty("description") String description,
		@JsonProperty("is_completed") boolean completed,
		@JsonProperty("ai_feedback_comment") String aiFeedbackComment,
		@JsonProperty("ai_feedback_at") String aiFeedbackAt
) {
}
