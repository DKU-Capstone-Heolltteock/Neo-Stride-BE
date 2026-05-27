package com.neostride.server.coaching.service;

import com.neostride.server.coaching.ai.AiCoachingClient;
import com.neostride.server.coaching.ai.AiCoachingPlan;
import com.neostride.server.coaching.ai.AiCoachingPlanDay;
import com.neostride.server.coaching.repository.CoachingRepository;
import com.neostride.server.coaching.repository.PlanDayInsertCommand;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AiPlanRefreshListener {

	private static final Logger logger = LoggerFactory.getLogger(AiPlanRefreshListener.class);
	private static final int DISTANCE_SCALE = 2;

	private final CoachingRepository coachingRepository;
	private final AiCoachingClient aiCoachingClient;

	public AiPlanRefreshListener(CoachingRepository coachingRepository, AiCoachingClient aiCoachingClient) {
		this.coachingRepository = coachingRepository;
		this.aiCoachingClient = aiCoachingClient;
	}

	@Async("coachingAiExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void refreshPlan(AiPlanRefreshRequest request) {
		if (request == null || request.planDates().isEmpty()) {
			return;
		}
		try {
			logger.info("AI plan refresh started for goal {} with {} dates", request.goalId(), request.planDates().size());
			AiCoachingPlan aiPlan = aiCoachingClient.generatePlan(request.goalRequest(), request.durationWeeks(), request.startDate(), request.planDates());
			List<PlanDayInsertCommand> aiPlanDays = toSafePlanDayCommands(
					aiPlan,
					request.planDates(),
					request.targetDistance(),
					request.targetPace()
			);
			if (aiPlanDays.isEmpty()) {
				logger.warn("AI plan refresh skipped for goal {} because generated plan was not usable; generated_days={} expected_days={}",
						request.goalId(),
						aiPlan == null || aiPlan.planDays() == null ? null : aiPlan.planDays().size(),
						request.planDates().size());
				return;
			}
			coachingRepository.updatePlanDayTargets(request.userId(), request.goalId(), aiPlanDays);
			logger.info("AI plan refresh applied for goal {} with {} days", request.goalId(), aiPlanDays.size());
		} catch (RuntimeException exception) {
			logger.warn("AI plan refresh failed for goal {}", request.goalId(), exception);
		}
	}

	private List<PlanDayInsertCommand> toSafePlanDayCommands(AiCoachingPlan aiPlan, List<LocalDate> expectedDates,
			BigDecimal targetDistance, Integer targetPace) {
		if (expectedDates == null || expectedDates.isEmpty() || aiPlan == null
				|| aiPlan.planDays() == null || aiPlan.planDays().size() != expectedDates.size()) {
			return List.of();
		}
		List<PlanDayInsertCommand> commands = new ArrayList<>();
		Set<LocalDate> seenDates = new HashSet<>();
		boolean allDaysEqualFinalGoal = expectedDates.size() > 1;
		for (LocalDate expectedDate : expectedDates) {
			AiCoachingPlanDay day = null;
			for (AiCoachingPlanDay candidate : aiPlan.planDays()) {
				if (candidate != null && expectedDate.equals(candidate.planDate())) {
					day = candidate;
					break;
				}
			}
			if (day == null || !seenDates.add(expectedDate)
					|| day.dayDistanceKm() == null || day.dayPaceSecPerKm() == null
					|| day.dayDistanceKm().compareTo(BigDecimal.ZERO) <= 0
					|| day.dayPaceSecPerKm() <= 0) {
				return List.of();
			}
			BigDecimal dayDistance = day.dayDistanceKm().setScale(DISTANCE_SCALE, RoundingMode.HALF_UP);
			Integer dayPace = day.dayPaceSecPerKm();
			allDaysEqualFinalGoal = allDaysEqualFinalGoal
					&& dayDistance.compareTo(targetDistance) == 0
					&& dayPace.equals(targetPace);
			commands.add(new PlanDayInsertCommand(expectedDate, dayDistance, dayPace));
		}
		return allDaysEqualFinalGoal ? List.of() : commands;
	}
}
