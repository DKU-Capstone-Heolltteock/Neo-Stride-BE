package com.neostride.server.coaching.service;

import com.neostride.server.coaching.ai.AiCoachingClient;
import com.neostride.server.coaching.dto.FeedbackRequest;
import com.neostride.server.coaching.dto.FeedbackResponse;
import com.neostride.server.coaching.dto.GoalRequest;
import com.neostride.server.coaching.dto.GoalResponse;
import com.neostride.server.coaching.dto.GoalStatusUpdateRequest;
import com.neostride.server.coaching.dto.PlanDayResponse;
import com.neostride.server.coaching.dto.TodayPlanResponse;
import com.neostride.server.coaching.repository.CoachingRepository;
import com.neostride.server.coaching.repository.GoalInsertCommand;
import com.neostride.server.coaching.repository.GoalRow;
import com.neostride.server.coaching.repository.PlanDayInsertCommand;
import com.neostride.server.coaching.repository.PlanDayRow;
import com.neostride.server.coaching.repository.PlanPerformanceRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachingService {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
	private static final int DISTANCE_SCALE = 2;
	private static final int MIN_PACE_SEC_PER_KM = 180;

	private final CoachingRepository coachingRepository;
	private final AiCoachingClient aiCoachingClient;
	private final ApplicationEventPublisher eventPublisher;
	private final CoachingPlanPolicy planPolicy = new CoachingPlanPolicy();
	private final CoachingFeedbackPolicy feedbackPolicy = new CoachingFeedbackPolicy();
	private final CoachingPlanAdjustmentPolicy planAdjustmentPolicy = new CoachingPlanAdjustmentPolicy();

	@Autowired
	public CoachingService(CoachingRepository coachingRepository, AiCoachingClient aiCoachingClient,
			ApplicationEventPublisher eventPublisher) {
		this.coachingRepository = coachingRepository;
		this.aiCoachingClient = aiCoachingClient;
		this.eventPublisher = eventPublisher;
	}

	CoachingService(CoachingRepository coachingRepository, AiCoachingClient aiCoachingClient) {
		this(coachingRepository, aiCoachingClient, event -> { });
	}

	@Transactional
	public GoalResponse createGoal(GoalRequest request) {
		validateGoalRequest(request);
		int durationWeeks = durationWeeks(request.periodType(), request.customWeeks());
		LocalDate startDate = parseDate(request.startDate(), "start_date");
		Set<DayOfWeek> runningDays = parseRunningDays(request.runningDays());
		BigDecimal targetDistance = request.goalDistanceKm().setScale(DISTANCE_SCALE, RoundingMode.HALF_UP);
		int targetPace = requirePositive(request.goalPaceSecPerKm(), "goal_pace_sec_per_km");
		List<LocalDate> planDates = scheduledPlanDates(startDate, durationWeeks, runningDays);
		List<PlanDayInsertCommand> planDays = generatePlanDays(planDates, targetDistance, targetPace);

		coachingRepository.deactivateActiveGoals(request.userId());
		long goalId = coachingRepository.insertGoal(new GoalInsertCommand(
				request.userId(),
				durationWeeks,
				runningDays.size(),
				targetDistance,
				targetPace
		));
		coachingRepository.insertPlanDays(request.userId(), goalId, planDays);
		publishAiPlanRefresh(request, goalId, durationWeeks, startDate, planDates, targetDistance, targetPace);

		GoalRow persisted = coachingRepository.findGoalById(goalId);
		if (persisted == null) {
			persisted = new GoalRow(goalId, request.userId(), durationWeeks, runningDays.size(), targetDistance, targetPace,
					LocalDateTime.now(), true, false, startDate, startDate.plusWeeks(durationWeeks).minusDays(1), request.runningDays());
		}
		return toGoalResponse(persisted, request.periodType(), request.customWeeks(), request.runningDays(), coachingRepository.findPlanDaysByGoalId(goalId));
	}

	@Transactional
	public GoalResponse getActiveGoal(long userId) {
		validatePositive(userId, "user_id");
		GoalRow goal = coachingRepository.findActiveGoalByUserId(userId);
		if (goal == null) {
			return GoalResponse.empty();
		}
		List<PlanDayRow> planDays = ensureProgressivePlanDays(goal, coachingRepository.findPlanDaysByGoalId(goal.goalId()));
		return toGoalResponse(goal, periodType(goal.durationWeeks()), customWeeks(goal.durationWeeks()), runningDaysOrDerived(goal.runningDays(), planDays), planDays);
	}

	@Transactional
	public TodayPlanResponse getTodayPlan(long userId) {
		validatePositive(userId, "user_id");
		PlanDayRow planDay = coachingRepository.findTodayPlanByUserId(userId, LocalDate.now());
		if (planDay == null) {
			return new TodayPlanResponse(false, null, null);
		}
		GoalRow goal = coachingRepository.findGoalById(planDay.goalId());
		List<PlanDayRow> goalPlanDays = goal == null ? List.of() : ensureProgressivePlanDays(goal, coachingRepository.findPlanDaysByGoalId(goal.goalId()));
		PlanDayRow normalizedToday = goalPlanDays.stream()
				.filter(day -> day.planDayId().equals(planDay.planDayId()))
				.findFirst()
				.orElse(planDay);
		return new TodayPlanResponse(true, toPlanDayResponse(normalizedToday), goal == null ? null : toGoalInfo(goal, periodType(goal.durationWeeks()), customWeeks(goal.durationWeeks()), runningDaysOrDerived(goal.runningDays(), goalPlanDays)));
	}

	@Transactional
	public FeedbackResponse requestFeedback(long userId, long planDayId, FeedbackRequest request) {
		validatePositive(userId, "user_id");
		validatePositive(planDayId, "plan_day_id");
		validateFeedbackRequest(planDayId, request);
		request = request.withResolvedActualPaceSecPerKm();
		PlanDayRow planDay = coachingRepository.findPlanDayByIdForUser(planDayId, userId);
		if (hasExistingFeedback(planDay)) {
			return new FeedbackResponse(planDayId, Boolean.TRUE.equals(planDay.completed()), planDay.feedback().trim(), feedbackUpdatedAt(planDay));
		}
		String feedback = buildFeedbackWithAiFallback(planDayId, request, planDay);
		if (!coachingRepository.updateFeedbackForUser(userId, planDayId, feedback)) {
			throw new IllegalArgumentException("plan_day_id에 해당하는 플랜이 없습니다.");
		}
		if (planDay != null) {
			adjustFuturePlanWithFeedbackLoop(userId, planDay, request);
		}
		return new FeedbackResponse(planDayId, true, feedback, LocalDateTime.now().format(DATE_TIME_FORMATTER));
	}

	private boolean hasExistingFeedback(PlanDayRow planDay) {
		return planDay != null && planDay.feedback() != null && !planDay.feedback().isBlank();
	}

	private String feedbackUpdatedAt(PlanDayRow planDay) {
		return planDay.updatedAt() == null ? null : planDay.updatedAt().format(DATE_TIME_FORMATTER);
	}

	@Transactional
	public FeedbackResponse completePlanWithRunningRecord(long userId, long planDayId, BigDecimal actualDistanceKm,
			Integer actualTimeSec, Integer actualPaceSecPerKm) {
		return requestFeedback(userId, planDayId, new FeedbackRequest(
				planDayId,
				normalizedFeedbackDecimal(actualDistanceKm, "actual_distance_km"),
				actualTimeSec,
				actualPaceSecPerKm
		));
	}

	@Transactional
	public void lockPlanForRunningRecordDeletion(long userId, long planDayId) {
		validatePositive(userId, "user_id");
		validatePositive(planDayId, "plan_day_id");
		if (coachingRepository.findPlanDayByIdForUser(planDayId, userId) == null) {
			throw new IllegalArgumentException("plan_day_id에 해당하는 플랜이 없습니다.");
		}
	}

	@Transactional
	public void restorePlanToPendingAfterRunningRecordDeleted(long userId, long planDayId) {
		validatePositive(userId, "user_id");
		validatePositive(planDayId, "plan_day_id");
		if (!coachingRepository.restorePlanToPendingForUser(userId, planDayId)) {
			throw new IllegalArgumentException("plan_day_id에 해당하는 플랜이 없습니다.");
		}
	}

	@Transactional
	public Map<String, String> deleteGoal(long userId, long goalId) {
		validatePositive(userId, "user_id");
		validatePositive(goalId, "goal_id");
		if (!coachingRepository.deleteGoalForUser(userId, goalId)) {
			throw new IllegalArgumentException("goal_id에 해당하는 목표가 없습니다.");
		}
		return Map.of("status", "success", "message", "삭제 완료");
	}

	@Transactional
	public GoalResponse updateGoalStatus(long userId, long goalId, GoalStatusUpdateRequest request) {
		validatePositive(userId, "user_id");
		validatePositive(goalId, "goal_id");
		validateGoalStatusUpdateRequest(request);
		if (!coachingRepository.updateGoalStatusForUser(userId, goalId, request.active(), request.achieved())) {
			throw new IllegalArgumentException("goal_id에 해당하는 목표가 없습니다.");
		}
		GoalRow goal = coachingRepository.findGoalByIdForUser(goalId, userId);
		if (goal == null) {
			throw new IllegalArgumentException("goal_id에 해당하는 목표가 없습니다.");
		}
		List<PlanDayRow> planDays = coachingRepository.findPlanDaysByGoalId(goalId);
		return toGoalResponse(goal, periodType(goal.durationWeeks()), customWeeks(goal.durationWeeks()), runningDaysOrDerived(goal.runningDays(), planDays), planDays);
	}

	private void validateGoalRequest(GoalRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		validatePositive(request.userId(), "user_id");
		durationWeeks(request.periodType(), request.customWeeks());
		parseDate(request.startDate(), "start_date");
		parseRunningDays(request.runningDays());
		requirePositive(request.goalDistanceKm(), "goal_distance_km");
		requirePositive(request.goalPaceSecPerKm(), "goal_pace_sec_per_km");
	}

	private void validateFeedbackRequest(long pathPlanDayId, FeedbackRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		validatePositive(request.planDayId(), "plan_day_id");
		if (request.planDayId() != pathPlanDayId) {
			throw new IllegalArgumentException("Path와 Body의 plan_day_id가 일치해야 합니다.");
		}
		requirePositive(request.actualDistanceKm(), "actual_distance_km");
		if (request.actualTimeSec() == null || request.actualTimeSec() <= 0) {
			throw new IllegalArgumentException("actual_time_sec는 1 이상의 값이어야 합니다.");
		}
		requirePositive(request.resolvedActualPaceSecPerKm(), "actual_pace_sec_per_km");
	}

	private void validateGoalStatusUpdateRequest(GoalStatusUpdateRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		if (request.active() == null) {
			throw new IllegalArgumentException("is_active는 필수입니다.");
		}
		if (request.achieved() == null) {
			throw new IllegalArgumentException("is_achieved는 필수입니다.");
		}
	}

	private void publishAiPlanRefresh(GoalRequest request, long goalId, int durationWeeks, LocalDate startDate,
			List<LocalDate> planDates, BigDecimal targetDistance, Integer targetPace) {
		if (planDates.isEmpty()) {
			return;
		}
		eventPublisher.publishEvent(new AiPlanRefreshRequest(
				request.userId(),
				goalId,
				new GoalRequest(
						request.userId(),
						request.periodType(),
						request.customWeeks(),
						List.copyOf(request.runningDays()),
						request.goalDistanceKm(),
						request.goalPaceSecPerKm(),
						request.startDate()
				),
				durationWeeks,
				startDate,
				planDates,
				targetDistance,
				targetPace
		));
	}

	private List<PlanDayInsertCommand> generatePlanDays(List<LocalDate> planDates, BigDecimal targetDistance, Integer targetPace) {
		return planPolicy.generatePlanDays(planDates, targetDistance, targetPace);
	}

	private List<LocalDate> scheduledPlanDates(LocalDate startDate, int durationWeeks, Set<DayOfWeek> runningDays) {
		return planPolicy.scheduledPlanDates(startDate, durationWeeks, runningDays);
	}

	private List<PlanDayRow> ensureProgressivePlanDays(GoalRow goal, List<PlanDayRow> planDays) {
		if (!isFlatFinalGoalPlan(goal, planDays)) {
			return planDays;
		}
		List<LocalDate> planDates = planDays.stream().map(PlanDayRow::planDate).toList();
		List<PlanDayInsertCommand> generated = generatePlanDays(planDates, goal.targetDistance(), goal.targetPace());
		coachingRepository.updatePlanDayTargets(goal.userId(), goal.goalId(), generated);
		List<PlanDayRow> normalized = new ArrayList<>();
		for (int index = 0; index < planDays.size(); index++) {
			PlanDayRow original = planDays.get(index);
			PlanDayInsertCommand command = generated.get(index);
			normalized.add(new PlanDayRow(original.planDayId(), original.userId(), original.goalId(), original.planDate(), command.targetDistance(), command.targetPace(), original.completed(), original.feedback(), original.updatedAt()));
		}
		return normalized;
	}

	private boolean isFlatFinalGoalPlan(GoalRow goal, List<PlanDayRow> planDays) {
		return planPolicy.isFlatFinalGoalPlan(goal, planDays);
	}

	private GoalResponse toGoalResponse(GoalRow goal, String periodType, Integer customWeeks, List<String> runningDays, List<PlanDayRow> planDays) {
		return GoalResponse.of(goal.goalId(), Boolean.TRUE.equals(goal.active()), status(goal), toGoalInfo(goal, periodType, customWeeks, runningDays), planDays.stream().map(this::toPlanDayResponse).toList());
	}

	private GoalResponse.GoalInfo toGoalInfo(GoalRow goal, String periodType, Integer customWeeks, List<String> runningDays) {
		return new GoalResponse.GoalInfo(
				goal.goalId(),
				periodType,
				customWeeks,
				runningDays == null ? List.of() : runningDays,
				goal.targetDistance(),
				goal.targetPace(),
				goal.startDate() == null ? null : goal.startDate().format(DATE_FORMATTER),
				goal.endDate() == null ? null : goal.endDate().format(DATE_FORMATTER),
				goal.createdAt() == null ? null : goal.createdAt().format(DATE_TIME_FORMATTER),
				Boolean.TRUE.equals(goal.active()),
				Boolean.TRUE.equals(goal.achieved())
		);
	}

	private PlanDayResponse toPlanDayResponse(PlanDayRow row) {
		return new PlanDayResponse(
				row.planDayId(),
				row.planDate().format(DATE_FORMATTER),
				row.targetDistance(),
				row.targetPace(),
				"목표 거리 " + row.targetDistance() + "km, 목표 페이스 " + formatPace(row.targetPace()) + "/km 러닝",
				Boolean.TRUE.equals(row.completed()),
				row.feedback(),
				row.feedback() == null ? null : row.updatedAt().format(DATE_TIME_FORMATTER)
		);
	}

	private String buildFeedbackWithAiFallback(long planDayId, FeedbackRequest request, PlanDayRow planDay) {
		if (planDay == null) {
			return feedbackPolicy.fallbackFeedback(request);
		}
		String aiFeedback = aiCoachingClient.generateFeedback(feedbackPolicy.aiRequest(planDayId, request, planDay));
		if (aiFeedback != null && !aiFeedback.isBlank()) {
			return aiFeedback.trim();
		}
		return feedbackPolicy.fallbackFeedback(request, planDay);
	}

	private void adjustFuturePlanWithFeedbackLoop(long userId, PlanDayRow planDay, FeedbackRequest request) {
		List<PlanPerformanceRow> performances = coachingRepository.findRecentPlanPerformances(userId, planDay.goalId(), 5);
		if (performances == null || performances.isEmpty()) {
			performances = List.of(new PlanPerformanceRow(
					planDay.planDayId(),
					planDay.targetDistance(),
					planDay.targetPace(),
					request.actualDistanceKm(),
					request.actualPaceSecPerKm()
			));
		}
		int duePlanDays = coachingRepository.countPlanDaysThrough(planDay.goalId(), planDay.planDate());
		int completedPlanDays = coachingRepository.countCompletedPlanDaysThrough(planDay.goalId(), planDay.planDate());
		CoachingPlanAdjustment adjustment = planAdjustmentPolicy.adjustmentFor(completedPlanDays, duePlanDays, performances);
		if (adjustment != null) {
			coachingRepository.adjustFuturePlanTargets(userId, planDay.goalId(), planDay.planDate(), adjustment.distanceFactor(), adjustment.paceFactor());
		}
	}

	private BigDecimal normalizedFeedbackDecimal(BigDecimal value, String fieldName) {
		requirePositive(value, fieldName);
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private String status(GoalRow goal) {
		if (Boolean.TRUE.equals(goal.achieved())) {
			return "completed";
		}
		if (!Boolean.TRUE.equals(goal.active())) {
			return "deleted";
		}
		return "active";
	}

	private int durationWeeks(String periodType, Integer customWeeks) {
		return planPolicy.durationWeeks(periodType, customWeeks);
	}

	private String periodType(Integer durationWeeks) {
		return planPolicy.periodType(durationWeeks);
	}

	private Integer customWeeks(Integer durationWeeks) {
		return planPolicy.customWeeks(durationWeeks);
	}

	private Set<DayOfWeek> parseRunningDays(List<String> values) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("running_days는 1개 이상 필요합니다.");
		}
		Set<DayOfWeek> days = new HashSet<>();
		for (String value : values) {
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException("running_days 항목은 비어 있을 수 없습니다.");
			}
			days.add(switch (value.trim().toLowerCase()) {
				case "mon" -> DayOfWeek.MONDAY;
				case "tue" -> DayOfWeek.TUESDAY;
				case "wed" -> DayOfWeek.WEDNESDAY;
				case "thu" -> DayOfWeek.THURSDAY;
				case "fri" -> DayOfWeek.FRIDAY;
				case "sat" -> DayOfWeek.SATURDAY;
				case "sun" -> DayOfWeek.SUNDAY;
				default -> throw new IllegalArgumentException("running_days 값이 올바르지 않습니다.");
			});
		}
		return days;
	}

	private LocalDate parseDate(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + "는 필수입니다.");
		}
		try {
			return LocalDate.parse(value, DATE_FORMATTER);
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException(fieldName + "는 yyyy-MM-dd 형식이어야 합니다.");
		}
	}

	private List<String> runningDaysOrDerived(List<String> runningDays, List<PlanDayRow> planDays) {
		if (runningDays != null && !runningDays.isEmpty()) {
			return runningDays;
		}
		List<String> derived = new ArrayList<>();
		if (planDays == null) {
			return derived;
		}
		for (PlanDayRow planDay : planDays) {
			if (planDay == null || planDay.planDate() == null) {
				continue;
			}
			String value = switch (planDay.planDate().getDayOfWeek()) {
				case MONDAY -> "mon";
				case TUESDAY -> "tue";
				case WEDNESDAY -> "wed";
				case THURSDAY -> "thu";
				case FRIDAY -> "fri";
				case SATURDAY -> "sat";
				case SUNDAY -> "sun";
			};
			if (!derived.contains(value)) {
				derived.add(value);
			}
		}
		return derived;
	}

	private String formatPace(Integer seconds) {
		return feedbackPolicy.formatPace(seconds);
	}

	private void requirePositive(BigDecimal value, String fieldName) {
		if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException(fieldName + "는 0보다 큰 값이어야 합니다.");
		}
	}

	private int requirePositive(Integer value, String fieldName) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(fieldName + "는 1 이상의 값이어야 합니다.");
		}
		return value;
	}

	private void validatePositive(Long value, String fieldName) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(fieldName + "는 1 이상의 값이어야 합니다.");
		}
	}

	private void validatePositive(long value, String fieldName) {
		if (value <= 0) {
			throw new IllegalArgumentException(fieldName + "는 1 이상의 값이어야 합니다.");
		}
	}
}
