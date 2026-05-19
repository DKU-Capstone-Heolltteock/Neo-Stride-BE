package com.neostride.server.coaching.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PlanDayRow(
		Long planDayId,
		Long userId,
		Long goalId,
		LocalDate planDate,
		BigDecimal targetDistance,
		BigDecimal targetPace,
		Boolean completed,
		String feedback,
		LocalDateTime updatedAt
) {
}
