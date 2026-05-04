package com.neostride.server.coaching.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "오늘 코칭 플랜 응답")
public record TodayPlanResponse(
		@JsonProperty("has_plan") boolean hasPlan,
		@JsonProperty("plan_day") PlanDayResponse planDay,
		@JsonProperty("goal") GoalResponse.GoalInfo goal
) {
}
