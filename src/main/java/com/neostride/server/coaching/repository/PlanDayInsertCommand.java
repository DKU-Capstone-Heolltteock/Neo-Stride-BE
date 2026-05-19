package com.neostride.server.coaching.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PlanDayInsertCommand(
		LocalDate planDate,
		BigDecimal targetDistance,
		BigDecimal targetPace
) {
}
