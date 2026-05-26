package com.neostride.server.coaching.repository;

import java.math.BigDecimal;

public record PlanPerformanceRow(
		Long planDayId,
		BigDecimal targetDistance,
		BigDecimal targetPace,
		BigDecimal actualDistance,
		BigDecimal actualPace
) {
}
