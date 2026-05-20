package com.neostride.server.coaching.service;

import com.neostride.server.coaching.ai.AiCoachingClient;
import com.neostride.server.coaching.ai.AiCoachingFeedbackRequest;
import com.neostride.server.coaching.ai.AiCoachingPlan;
import com.neostride.server.coaching.ai.AiCoachingPlanDay;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachingService {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final CoachingRepository coachingRepository;
	private final AiCoachingClient aiCoachingClient;

	public CoachingService(CoachingRepository coachingRepository, AiCoachingClient aiCoachingClient) {
		this.coachingRepository = coachingRepository;
		this.aiCoachingClient = aiCoachingClient;
	}

	@Transactional
	public GoalResponse createGoal(GoalRequest request) {
		validateGoalRequest(request);
		int durationWeeks = durationWeeks(request.periodType(), request.customWeeks());
		LocalDate startDate = parseDate(request.startDate(), "start_date");
		Set<DayOfWeek> runningDays = parseRunningDays(request.runningDays());
		BigDecimal targetPace = normalizedPace(request.goalPaceMinPerKm(), "goal_pace_min_per_km");

		coachingRepository.deactivateActiveGoals(request.userId());
		long goalId = coachingRepository.insertGoal(new GoalInsertCommand(
				request.userId(),
				durationWeeks,
				runningDays.size(),
				request.goalDistanceKm().setScale(2, RoundingMode.HALF_UP),
				targetPace
		));
		coachingRepository.insertPlanDays(
				request.userId(),
				goalId,
				generatePlanDaysWithAiFallback(request, startDate, durationWeeks, runningDays, targetPace)
		);

		GoalRow persisted = coachingRepository.findGoalById(goalId);
		if (persisted == null) {
			persisted = new GoalRow(goalId, request.userId(), durationWeeks, runningDays.size(), request.goalDistanceKm(), targetPace,
					LocalDateTime.now(), true, false, startDate, startDate.plusWeeks(durationWeeks).minusDays(1), request.runningDays());
		}
		return toGoalResponse(persisted, request.periodType(), request.customWeeks(), request.runningDays(), coachingRepository.findPlanDaysByGoalId(goalId));
	}

	@Transactional(readOnly = true)
	public GoalResponse getActiveGoal(long userId) {
		validatePositive(userId, "user_id");
		GoalRow goal = coachingRepository.findActiveGoalByUserId(userId);
		if (goal == null) {
			return GoalResponse.empty();
		}
		List<PlanDayRow> planDays = coachingRepository.findPlanDaysByGoalId(goal.goalId());
		return toGoalResponse(goal, periodType(goal.durationWeeks()), customWeeks(goal.durationWeeks()), runningDaysOrDerived(goal.runningDays(), planDays), planDays);
	}

	@Transactional(readOnly = true)
	public TodayPlanResponse getTodayPlan(long userId) {
		validatePositive(userId, "user_id");
		PlanDayRow planDay = coachingRepository.findTodayPlanByUserId(userId, LocalDate.now());
		if (planDay == null) {
			return new TodayPlanResponse(false, null, null);
		}
		GoalRow goal = coachingRepository.findGoalById(planDay.goalId());
		List<PlanDayRow> goalPlanDays = goal == null ? List.of() : coachingRepository.findPlanDaysByGoalId(goal.goalId());
		return new TodayPlanResponse(true, toPlanDayResponse(planDay), goal == null ? null : toGoalInfo(goal, periodType(goal.durationWeeks()), customWeeks(goal.durationWeeks()), runningDaysOrDerived(goal.runningDays(), goalPlanDays)));
	}

	@Transactional
	public FeedbackResponse requestFeedback(long userId, long planDayId, FeedbackRequest request) {
		validatePositive(userId, "user_id");
		validatePositive(planDayId, "plan_day_id");
		validateFeedbackRequest(planDayId, request);
		String feedback = buildFeedbackWithAiFallback(userId, planDayId, request);
		if (!coachingRepository.updateFeedbackForUser(userId, planDayId, feedback)) {
			throw new IllegalArgumentException("plan_day_id에 해당하는 플랜이 없습니다.");
		}
		return new FeedbackResponse(planDayId, true, feedback, LocalDateTime.now().format(DATE_TIME_FORMATTER));
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
		requirePositive(request.goalPaceMinPerKm(), "goal_pace_min_per_km");
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
		requirePositive(request.actualPaceMinPerKm(), "actual_pace_min_per_km");
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

	private List<PlanDayInsertCommand> generatePlanDaysWithAiFallback(GoalRequest request, LocalDate startDate, int durationWeeks,
			Set<DayOfWeek> runningDays, BigDecimal targetPace) {
		AiCoachingPlan aiPlan = aiCoachingClient.generatePlan(request, durationWeeks, startDate);
		List<PlanDayInsertCommand> aiPlanDays = toSafePlanDayCommands(aiPlan, startDate, durationWeeks, runningDays);
		if (!aiPlanDays.isEmpty()) {
			return aiPlanDays;
		}
		return generatePlanDays(startDate, durationWeeks, runningDays, request.goalDistanceKm(), targetPace);
	}

	private List<PlanDayInsertCommand> toSafePlanDayCommands(AiCoachingPlan aiPlan, LocalDate startDate, int durationWeeks,
			Set<DayOfWeek> runningDays) {
		if (aiPlan == null || aiPlan.planDays() == null || aiPlan.planDays().isEmpty()) {
			return List.of();
		}
		LocalDate endExclusive = startDate.plusWeeks(durationWeeks);
		List<PlanDayInsertCommand> commands = new ArrayList<>();
		for (AiCoachingPlanDay day : aiPlan.planDays()) {
			if (day == null || day.planDate() == null || day.dayDistanceKm() == null || day.dayPaceMinPerKm() == null) {
				return List.of();
			}
			if (day.planDate().isBefore(startDate) || !day.planDate().isBefore(endExclusive)) {
				return List.of();
			}
			if (!runningDays.contains(day.planDate().getDayOfWeek())) {
				return List.of();
			}
			if (day.dayDistanceKm().compareTo(BigDecimal.ZERO) <= 0 || day.dayPaceMinPerKm().compareTo(BigDecimal.ZERO) <= 0) {
				return List.of();
			}
			commands.add(new PlanDayInsertCommand(day.planDate(), day.dayDistanceKm().setScale(2, RoundingMode.HALF_UP), normalizedPace(day.dayPaceMinPerKm(), "day_pace_min_per_km")));
		}
		return commands;
	}

	private List<PlanDayInsertCommand> generatePlanDays(LocalDate startDate, int durationWeeks, Set<DayOfWeek> runningDays,
			BigDecimal targetDistance, BigDecimal targetPace) {
		List<PlanDayInsertCommand> planDays = new ArrayList<>();
		LocalDate endExclusive = startDate.plusWeeks(durationWeeks);
		for (LocalDate date = startDate; date.isBefore(endExclusive); date = date.plusDays(1)) {
			if (runningDays.contains(date.getDayOfWeek())) {
				planDays.add(new PlanDayInsertCommand(date, targetDistance.setScale(2, RoundingMode.HALF_UP), targetPace));
			}
		}
		return planDays;
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
				"목표 거리 " + row.targetDistance() + "km, 목표 페이스 " + row.targetPace() + "분/km 러닝",
				Boolean.TRUE.equals(row.completed()),
				row.feedback(),
				row.feedback() == null ? null : row.updatedAt().format(DATE_TIME_FORMATTER)
		);
	}

	private String buildFeedbackWithAiFallback(long userId, long planDayId, FeedbackRequest request) {
		PlanDayRow planDay = coachingRepository.findPlanDayByIdForUser(planDayId, userId);
		if (planDay == null) {
			return buildFeedback(request);
		}
		String aiFeedback = aiCoachingClient.generateFeedback(new AiCoachingFeedbackRequest(
				planDayId,
				planDay.planDate(),
				planDay.targetDistance(),
				planDay.targetPace(),
				delta(request.actualDistanceKm(), planDay.targetDistance()),
				delta(request.actualPaceMinPerKm(), planDay.targetPace()),
				request
		));
		if (aiFeedback != null && !aiFeedback.isBlank()) {
			return aiFeedback.trim();
		}
		return buildFeedback(request);
	}

	private BigDecimal delta(BigDecimal actual, BigDecimal target) {
		if (actual == null || target == null) {
			return null;
		}
		return actual.subtract(target).setScale(2, RoundingMode.HALF_UP);
	}

	private String buildFeedback(FeedbackRequest request) {
		return "러닝 완료: " + request.actualDistanceKm().setScale(2, RoundingMode.HALF_UP)
				+ "km, " + request.actualTimeSec() + "초, 평균 페이스 "
				+ request.actualPaceMinPerKm().setScale(2, RoundingMode.HALF_UP) + "분/km";
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

	private String periodType(Integer durationWeeks) {
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

	private Integer customWeeks(Integer durationWeeks) {
		String periodType = periodType(durationWeeks);
		return "custom".equals(periodType) ? durationWeeks : 0;
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

	private BigDecimal normalizedPace(BigDecimal value, String fieldName) {
		requirePositive(value, fieldName);
		return value.setScale(2, RoundingMode.HALF_UP);
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

	private void requirePositive(BigDecimal value, String fieldName) {
		if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException(fieldName + "는 0보다 큰 값이어야 합니다.");
		}
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
