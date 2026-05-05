package com.neostride.server.coaching.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "코칭 목표 응답")
public record GoalResponse(
		@JsonProperty("goal_id") Long goalId,
		@JsonProperty("has_active_goal") boolean hasActiveGoal,
		@JsonProperty("status") String status,
		@JsonProperty("goal") GoalInfo goal,
		@JsonProperty("plan_days") List<PlanDayResponse> planDays
) {
	public static GoalResponse of(Long goalId, boolean hasActiveGoal, String status, GoalInfo goal, List<PlanDayResponse> planDays) {
		return new GoalResponse(goalId, hasActiveGoal, status, goal, planDays == null ? List.of() : planDays);
	}

	public static GoalResponse empty() {
		return new GoalResponse(null, false, null, null, List.of());
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Schema(description = "코칭 목표 상세 정보")
	public record GoalInfo(
			@JsonProperty("goal_id") Long goalId,
			@JsonProperty("period_type") String periodType,
			@JsonProperty("custom_weeks") Integer customWeeks,
			@JsonProperty("running_days") List<String> runningDays,
			@JsonProperty("goal_distance_km") BigDecimal goalDistanceKm,
			@JsonProperty("goal_pace_min_per_km") BigDecimal goalPaceMinPerKm,
			@JsonProperty("start_date") String startDate,
			@JsonProperty("end_date") String endDate,
			@JsonProperty("created_at") String createdAt,
			@JsonProperty("is_active") Boolean active,
			@JsonProperty("is_achieved") Boolean achieved
	) {
	}
}
