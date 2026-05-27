package com.neostride.server.coaching.repository;

import java.math.BigDecimal;

public record PlanPerformanceRow(
		Long planDayId,
		BigDecimal targetDistance,
		Integer targetPace,
		BigDecimal actualDistance,
		Integer actualPace
) {
}
