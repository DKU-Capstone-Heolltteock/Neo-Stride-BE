package com.neostride.server.coaching.service;

import com.neostride.server.coaching.repository.GoalRow;
import com.neostride.server.coaching.repository.PlanDayInsertCommand;
import com.neostride.server.coaching.repository.PlanDayRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class CoachingPlanPolicy {
	private static final int DISTANCE_SCALE = 2;
	private static final int MIN_PACE_SEC_PER_KM = 180;

	int durationWeeks(String periodType, Integer customWeeks) {
		if (periodType == null || periodType.isBlank()) {
			throw new IllegalArgumentException("period_type은 필수입니다.");
		}
		return switch (periodType) {
			case "1month" -> 4;
			case "3month" -> 12;
			case "6month" -> 24;
			case "1year" -> 52;
			case "custom" -> {
				if (customWeeks == null || customWeeks <= 0) {
					throw new IllegalArgumentException("custom_weeks는 1 이상의 값이어야 합니다.");
				}
				yield customWeeks;
			}
			default -> throw new IllegalArgumentException("period_type 값이 올바르지 않습니다.");
		};
	}

	String periodType(Integer durationWeeks) {
		if (durationWeeks == null) {
			return "custom";
		}
		return switch (durationWeeks) {
			case 4 -> "1month";
			case 12 -> "3month";
			case 24 -> "6month";
			case 52 -> "1year";
			default -> "custom";
		};
	}

	Integer customWeeks(Integer durationWeeks) {
		return "custom".equals(periodType(durationWeeks)) ? durationWeeks : 0;
	}

	List<LocalDate> scheduledPlanDates(LocalDate startDate, int durationWeeks, Set<DayOfWeek> runningDays) {
		List<LocalDate> dates = new ArrayList<>();
		LocalDate endExclusive = startDate.plusWeeks(durationWeeks);
		for (LocalDate date = startDate; date.isBefore(endExclusive); date = date.plusDays(1)) {
			if (runningDays.contains(date.getDayOfWeek())) {
				dates.add(date);
			}
		}
		return dates;
	}

	List<PlanDayInsertCommand> generatePlanDays(List<LocalDate> planDates, BigDecimal targetDistance, Integer targetPace) {
		List<PlanDayInsertCommand> planDays = new ArrayList<>();
		for (int index = 0; index < planDates.size(); index++) {
			planDays.add(new PlanDayInsertCommand(
					planDates.get(index),
					progressiveDistance(targetDistance, index, planDates.size()),
					progressivePace(targetPace, index, planDates.size())
			));
		}
		return planDays;
	}

	boolean isFlatFinalGoalPlan(GoalRow goal, List<PlanDayRow> planDays) {
		if (goal == null || planDays == null || planDays.size() <= 1 || goal.targetDistance() == null || goal.targetPace() == null) {
			return false;
		}
		for (PlanDayRow planDay : planDays) {
			if (planDay == null || planDay.targetDistance() == null || planDay.targetPace() == null
					|| planDay.targetDistance().compareTo(goal.targetDistance()) != 0
					|| !planDay.targetPace().equals(goal.targetPace())) {
				return false;
			}
		}
		return true;
	}

	private BigDecimal progressiveDistance(BigDecimal targetDistance, int index, int totalDays) {
		if (totalDays <= 1) {
			return targetDistance.setScale(DISTANCE_SCALE, RoundingMode.HALF_UP);
		}
		BigDecimal progress = BigDecimal.valueOf(index).divide(BigDecimal.valueOf(totalDays - 1L), 4, RoundingMode.HALF_UP);
		BigDecimal factor = new BigDecimal("0.60").add(new BigDecimal("0.40").multiply(progress));
		return targetDistance.multiply(factor).max(new BigDecimal("0.10")).setScale(DISTANCE_SCALE, RoundingMode.HALF_UP);
	}

	private Integer progressivePace(Integer targetPace, int index, int totalDays) {
		if (totalDays <= 1) {
			return targetPace;
		}
		BigDecimal progress = BigDecimal.valueOf(index).divide(BigDecimal.valueOf(totalDays - 1L), 4, RoundingMode.HALF_UP);
		BigDecimal factor = new BigDecimal("1.12").subtract(new BigDecimal("0.12").multiply(progress));
		return BigDecimal.valueOf(targetPace)
				.multiply(factor)
				.max(BigDecimal.valueOf(MIN_PACE_SEC_PER_KM))
				.setScale(0, RoundingMode.HALF_UP)
				.intValueExact();
	}
}
