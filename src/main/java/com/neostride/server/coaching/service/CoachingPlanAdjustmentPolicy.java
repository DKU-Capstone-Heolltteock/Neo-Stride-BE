package com.neostride.server.coaching.service;

import com.neostride.server.coaching.repository.PlanPerformanceRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

final class CoachingPlanAdjustmentPolicy {
	CoachingPlanAdjustment adjustmentFor(int completedPlanDays, int duePlanDays, List<PlanPerformanceRow> performances) {
		if (duePlanDays <= 0) {
			return null;
		}
		BigDecimal completionRate = BigDecimal.valueOf(completedPlanDays)
				.divide(BigDecimal.valueOf(duePlanDays), 4, RoundingMode.HALF_UP);
		BigDecimal averageDistanceRatio = averageDistanceRatio(performances);
		BigDecimal averagePaceRatio = averagePaceRatio(performances);

		if (completionRate.compareTo(new BigDecimal("0.80")) >= 0
				&& averageDistanceRatio.compareTo(new BigDecimal("1.00")) >= 0
				&& averagePaceRatio.compareTo(new BigDecimal("1.00")) <= 0) {
			return new CoachingPlanAdjustment(new BigDecimal("1.05"), new BigDecimal("0.97"));
		}

		if (completionRate.compareTo(new BigDecimal("0.60")) < 0
				|| averageDistanceRatio.compareTo(new BigDecimal("0.85")) < 0
				|| averagePaceRatio.compareTo(new BigDecimal("1.10")) > 0) {
			return new CoachingPlanAdjustment(new BigDecimal("0.90"), new BigDecimal("1.05"));
		}

		return null;
	}

	private BigDecimal averageDistanceRatio(List<PlanPerformanceRow> performances) {
		BigDecimal total = BigDecimal.ZERO;
		int count = 0;
		for (PlanPerformanceRow performance : performances) {
			if (performance == null || performance.actualDistance() == null
					|| performance.targetDistance() == null || performance.targetDistance().compareTo(BigDecimal.ZERO) <= 0) {
				continue;
			}
			total = total.add(performance.actualDistance().divide(performance.targetDistance(), 4, RoundingMode.HALF_UP));
			count++;
		}
		return count == 0 ? BigDecimal.ONE : total.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
	}

	private BigDecimal averagePaceRatio(List<PlanPerformanceRow> performances) {
		BigDecimal total = BigDecimal.ZERO;
		int count = 0;
		for (PlanPerformanceRow performance : performances) {
			if (performance == null || performance.actualPace() == null
					|| performance.targetPace() == null || performance.targetPace() <= 0) {
				continue;
			}
			total = total.add(BigDecimal.valueOf(performance.actualPace())
					.divide(BigDecimal.valueOf(performance.targetPace()), 4, RoundingMode.HALF_UP));
			count++;
		}
		return count == 0 ? BigDecimal.ONE : total.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
	}
}

record CoachingPlanAdjustment(BigDecimal distanceFactor, BigDecimal paceFactor) {}
