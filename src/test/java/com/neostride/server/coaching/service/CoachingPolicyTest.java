package com.neostride.server.coaching.service;

import com.neostride.server.coaching.dto.FeedbackRequest;
import com.neostride.server.coaching.repository.PlanDayRow;
import com.neostride.server.coaching.repository.PlanPerformanceRow;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoachingPolicyTest {
	private final CoachingPlanPolicy planPolicy = new CoachingPlanPolicy();
	private final CoachingFeedbackPolicy feedbackPolicy = new CoachingFeedbackPolicy();
	private final CoachingPlanAdjustmentPolicy adjustmentPolicy = new CoachingPlanAdjustmentPolicy();

	@Test
	void generatePlanDays_preservesProgressiveFallbackTargets() {
		List<LocalDate> dates = planPolicy.scheduledPlanDates(
				LocalDate.parse("2026-05-04"),
				1,
				Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
		);

		var days = planPolicy.generatePlanDays(dates, new BigDecimal("10.00"), 360);

		assertThat(dates).containsExactly(LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-06"));
		assertThat(days.get(0).targetDistance()).isEqualByComparingTo(new BigDecimal("6.00"));
		assertThat(days.get(0).targetPace()).isEqualTo(403);
		assertThat(days.get(1).targetDistance()).isEqualByComparingTo(new BigDecimal("10.00"));
		assertThat(days.get(1).targetPace()).isEqualTo(360);
	}

	@Test
	void fallbackFeedback_preservesCoachingMessageShape() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("5.50"), 1815, 330);
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 360, false, null, null);

		String feedback = feedbackPolicy.fallbackFeedback(request, planDay);

		assertThat(feedback)
				.contains("평균 페이스 5:30/km")
				.contains("목표보다 0.50km 더 달렸고")
				.contains("목표보다 0:30/km 빨랐습니다.");
	}

	@Test
	void adjustmentPolicy_preservesAccelerationAndRecoveryFactors() {
		CoachingPlanAdjustment acceleration = adjustmentPolicy.adjustmentFor(5, 5, List.of(
				new PlanPerformanceRow(20L, new BigDecimal("5.00"), 360, new BigDecimal("5.20"), 350)
		));
		CoachingPlanAdjustment recovery = adjustmentPolicy.adjustmentFor(1, 5, List.of(
				new PlanPerformanceRow(21L, new BigDecimal("5.00"), 360, new BigDecimal("4.00"), 430)
		));

		assertThat(acceleration.distanceFactor()).isEqualByComparingTo(new BigDecimal("1.05"));
		assertThat(acceleration.paceFactor()).isEqualByComparingTo(new BigDecimal("0.97"));
		assertThat(recovery.distanceFactor()).isEqualByComparingTo(new BigDecimal("0.90"));
		assertThat(recovery.paceFactor()).isEqualByComparingTo(new BigDecimal("1.05"));
	}
}
