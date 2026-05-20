package com.neostride.server.coaching.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record GoalRow(
		Long goalId,
		Long userId,
		Integer durationWeeks,
		Integer runningDay,
		BigDecimal targetDistance,
		BigDecimal targetPace,
		LocalDateTime createdAt,
		Boolean active,
		Boolean achieved,
		LocalDate startDate,
		LocalDate endDate,
		List<String> runningDays
) {
}
