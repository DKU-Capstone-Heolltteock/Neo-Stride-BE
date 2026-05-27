package com.neostride.server.coaching.service;

import com.neostride.server.coaching.dto.GoalRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AiPlanRefreshRequest(
		long userId,
		long goalId,
		GoalRequest goalRequest,
		int durationWeeks,
		LocalDate startDate,
		List<LocalDate> planDates,
		BigDecimal targetDistance,
		Integer targetPace
) {
	public AiPlanRefreshRequest {
		planDates = planDates == null ? List.of() : List.copyOf(planDates);
	}
}
