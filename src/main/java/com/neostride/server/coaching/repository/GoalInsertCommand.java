package com.neostride.server.coaching.repository;

import java.math.BigDecimal;

public record GoalInsertCommand(
		Long userId,
		Integer durationWeeks,
		Integer runningDay,
		BigDecimal targetDistance,
		BigDecimal targetPace
) {
}
