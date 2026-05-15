package com.neostride.server.coaching.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiCoachingPlan(
		@JsonProperty("summary") String summary,
		@JsonProperty("plan_days") List<AiCoachingPlanDay> planDays
) {
}
