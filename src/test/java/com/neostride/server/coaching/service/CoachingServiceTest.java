package com.neostride.server.coaching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neostride.server.coaching.ai.AiCoachingClient;
import com.neostride.server.coaching.ai.AiCoachingFeedbackRequest;
import com.neostride.server.coaching.ai.AiCoachingPlan;
import com.neostride.server.coaching.ai.AiCoachingPlanDay;
import com.neostride.server.coaching.dto.FeedbackRequest;
import com.neostride.server.coaching.dto.GoalRequest;
import com.neostride.server.coaching.dto.GoalStatusUpdateRequest;
import com.neostride.server.coaching.repository.CoachingRepository;
import com.neostride.server.coaching.repository.GoalRow;
import com.neostride.server.coaching.repository.PlanDayRow;
import com.neostride.server.coaching.repository.PlanPerformanceRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoachingServiceTest {

	private final CoachingRepository repository = mock(CoachingRepository.class);
	private final AiCoachingClient aiCoachingClient = mock(AiCoachingClient.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final CoachingService service = new CoachingService(repository, aiCoachingClient, eventPublisher);

	@Test
	void createGoal_insertsGoalAndGeneratedPlanDays() {
		GoalRequest request = new GoalRequest(1L, "1month", 0, List.of("mon", "wed", "fri"), new BigDecimal("5.0"), 390, "2026-04-30");
		when(repository.insertGoal(any())).thenReturn(10L);
		when(repository.findPlanDaysByGoalId(10L)).thenReturn(List.of());

		var response = service.createGoal(request);

		assertThat(response.goalId()).isEqualTo(10L);
		assertThat(response.hasActiveGoal()).isTrue();
		assertThat(response.status()).isEqualTo("active");
		verify(repository).deactivateActiveGoals(1L);
		verify(repository).insertPlanDays(eq(1L), eq(10L), any());
	}

	@Test
	void createGoal_persistsProgressivePlanAndPublishesAiRefreshWithoutWaitingForAi() {
		GoalRequest request = new GoalRequest(1L, "custom", 1, List.of("mon", "wed"), new BigDecimal("5.0"), 390, "2026-05-04");
		when(repository.insertGoal(any())).thenReturn(10L);
		when(repository.findPlanDaysByGoalId(10L)).thenReturn(List.of());

		service.createGoal(request);

		verify(aiCoachingClient, never()).generatePlan(any(), anyInt(), any(), any());
		verify(repository).insertGoal(argThat(command ->
				command.targetDistance().compareTo(new BigDecimal("5.00")) == 0
						&& command.targetPace().equals(390)
		));
		verify(repository).insertPlanDays(eq(1L), eq(10L), argThat(commands ->
				commands.size() == 2
						&& commands.get(0).planDate().equals(LocalDate.parse("2026-05-04"))
						&& commands.get(0).targetDistance().compareTo(new BigDecimal("3.00")) == 0
						&& commands.get(0).targetPace().equals(437)
						&& commands.get(1).planDate().equals(LocalDate.parse("2026-05-06"))
						&& commands.get(1).targetDistance().compareTo(new BigDecimal("5.00")) == 0
						&& commands.get(1).targetPace().equals(390)
		));

		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue()).isInstanceOf(AiPlanRefreshRequest.class);
		AiPlanRefreshRequest refreshRequest = (AiPlanRefreshRequest) eventCaptor.getValue();
		assertThat(refreshRequest.userId()).isEqualTo(1L);
		assertThat(refreshRequest.goalId()).isEqualTo(10L);
		assertThat(refreshRequest.goalRequest().goalPaceSecPerKm()).isEqualTo(390);
		assertThat(refreshRequest.durationWeeks()).isEqualTo(1);
		assertThat(refreshRequest.planDates()).containsExactly(LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-06"));
	}

	@Test
	void aiPlanRefreshUpdatesUncompletedPlanTargetsWhenAiPlanIsValid() {
		AiPlanRefreshListener listener = new AiPlanRefreshListener(repository, aiCoachingClient);
		GoalRequest goalRequest = new GoalRequest(1L, "custom", 1, List.of("mon", "wed"), new BigDecimal("5.0"), 390, "2026-05-04");
		AiPlanRefreshRequest refreshRequest = new AiPlanRefreshRequest(
				1L,
				10L,
				goalRequest,
				1,
				LocalDate.parse("2026-05-04"),
				List.of(LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-06")),
				new BigDecimal("5.00"),
				390
		);
		when(aiCoachingClient.generatePlan(goalRequest, 1, LocalDate.parse("2026-05-04"), List.of(LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-06"))))
				.thenReturn(new AiCoachingPlan("AI 맞춤 1주 플랜", List.of(
						new AiCoachingPlanDay(LocalDate.parse("2026-05-04"), new BigDecimal("2.50"), 435, "회복 조깅"),
						new AiCoachingPlanDay(LocalDate.parse("2026-05-06"), new BigDecimal("3.00"), 390, "가벼운 지속주")
				)));

		listener.refreshPlan(refreshRequest);

		verify(repository).updatePlanDayTargets(eq(1L), eq(10L), argThat(commands ->
				commands.size() == 2
						&& commands.get(0).targetDistance().compareTo(new BigDecimal("2.50")) == 0
						&& commands.get(0).targetPace().equals(435)
						&& commands.get(1).targetDistance().compareTo(new BigDecimal("3.00")) == 0
						&& commands.get(1).targetPace().equals(390)
		));
	}

	@Test
	void createGoal_usesProgressiveDailyTargetsWhenAiPlanIsUnavailable() {
		GoalRequest request = new GoalRequest(1L, "custom", 1, List.of("mon", "wed", "fri"), new BigDecimal("10.0"), 300, "2026-05-04");
		when(repository.insertGoal(any())).thenReturn(10L);
		when(repository.findPlanDaysByGoalId(10L)).thenReturn(List.of());

		service.createGoal(request);

		verify(repository).insertPlanDays(eq(1L), eq(10L), argThat(commands ->
				commands.size() == 3
						&& commands.get(0).targetDistance().compareTo(new BigDecimal("6.00")) == 0
						&& commands.get(0).targetPace().equals(336)
						&& commands.get(1).targetDistance().compareTo(new BigDecimal("8.00")) == 0
						&& commands.get(1).targetPace().equals(318)
						&& commands.get(2).targetDistance().compareTo(new BigDecimal("10.00")) == 0
						&& commands.get(2).targetPace().equals(300)
		));
	}

	@Test
	void createGoal_preservesSecondBasedPaceWithoutMinuteRounding() {
		GoalRequest request = new GoalRequest(1L, "custom", 1, List.of("mon"), new BigDecimal("5.4"), 344, "2026-05-04");
		when(repository.insertGoal(any())).thenReturn(10L);
		when(repository.findPlanDaysByGoalId(10L)).thenReturn(List.of());

		service.createGoal(request);

		verify(repository).insertGoal(argThat(command ->
				command.targetDistance().compareTo(new BigDecimal("5.40")) == 0
						&& command.targetPace().equals(344)
		));
		verify(repository).insertPlanDays(eq(1L), eq(10L), argThat(commands ->
				!commands.isEmpty() && commands.getFirst().targetPace().equals(344)
		));
	}

	@Test
	void getActiveGoal_derivesRunningDaysFromPlanDatesWhenGoalRowHasNoDayList() {
		GoalRow goal = new GoalRow(10L, 1L, 4, 2, new BigDecimal("5.00"), 387,
				LocalDateTime.parse("2026-05-01T09:00:00"), true, false,
				LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-06"), List.of());
		when(repository.findActiveGoalByUserId(1L)).thenReturn(goal);
		when(repository.findPlanDaysByGoalId(10L)).thenReturn(List.of(
				new PlanDayRow(100L, 1L, 10L, LocalDate.parse("2026-05-04"), new BigDecimal("5.00"), 387, false, null, LocalDateTime.parse("2026-05-01T09:00:00")),
				new PlanDayRow(101L, 1L, 10L, LocalDate.parse("2026-05-06"), new BigDecimal("5.00"), 387, false, null, LocalDateTime.parse("2026-05-01T09:00:00"))
		));

		var response = service.getActiveGoal(1L);

		assertThat(response.goal().runningDays()).containsExactly("mon", "wed");
		assertThat(response.goal().goalPaceSecPerKm()).isEqualTo(387);
	}

	@Test
	void getActiveGoal_returnsEmptyShapeWhenNoActiveGoalExists() {
		when(repository.findActiveGoalByUserId(1L)).thenReturn(null);

		var response = service.getActiveGoal(1L);

		assertThat(response.hasActiveGoal()).isFalse();
		assertThat(response.goal()).isNull();
		assertThat(response.planDays()).isEmpty();
	}

	@Test
	void requestFeedback_rejectsPathBodyMismatch() {
		FeedbackRequest request = new FeedbackRequest(21L, new BigDecimal("3.2"), 1240, 387);

		assertThatThrownBy(() -> service.requestFeedback(1L, 20L, request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("plan_day_id");
	}

	@Test
	void requestFeedback_marksPlanCompletedAndReturnsFeedback() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("3.2"), 1240, 387);
		when(repository.updateFeedbackForUser(eq(1L), eq(20L), any())).thenReturn(true);

		var response = service.requestFeedback(1L, 20L, request);

		assertThat(response.planDayId()).isEqualTo(20L);
		assertThat(response.completed()).isTrue();
		assertThat(response.aiFeedbackComment()).isNotBlank();
	}

	@Test
	void requestFeedback_fallbackBuildsCoachingCommentWhenAiClientReturnsNoComment() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("3.20"), 1240, 387);
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 360, false, null, LocalDateTime.parse("2026-05-01T09:00:00"));
		when(repository.findPlanDayByIdForUser(20L, 1L)).thenReturn(planDay);
		when(aiCoachingClient.generateFeedback(any())).thenReturn(null);
		when(repository.updateFeedbackForUser(eq(1L), eq(20L), any())).thenReturn(true);

		var response = service.requestFeedback(1L, 20L, request);

		assertThat(response.aiFeedbackComment())
				.contains("평균 페이스 6:27/km")
				.contains("목표보다 1.80km 부족")
				.contains("회복");
		verify(repository).updateFeedbackForUser(eq(1L), eq(20L), argThat(feedback -> !feedback.startsWith("러닝 완료:")));
	}

	@Test
	void requestFeedback_returnsExistingFeedbackWithoutAiCallOrPlanAdjustment() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("3.2"), 1240, 387);
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 360, true, "Existing AI feedback", LocalDateTime.parse("2026-05-04T10:30:00"));
		when(repository.findPlanDayByIdForUser(20L, 1L)).thenReturn(planDay);

		var response = service.requestFeedback(1L, 20L, request);

		assertThat(response.completed()).isTrue();
		assertThat(response.aiFeedbackComment()).isEqualTo("Existing AI feedback");
		assertThat(response.aiFeedbackAt()).isEqualTo("2026-05-04T10:30:00");
		verify(aiCoachingClient, never()).generateFeedback(any());
		verify(repository, never()).updateFeedbackForUser(eq(1L), eq(20L), any());
		verify(repository, never()).findRecentPlanPerformances(eq(1L), eq(10L), eq(5));
	}

	@Test
	void requestFeedback_acceptsMinutePaceAliasAndConvertsToSeconds() throws Exception {
		FeedbackRequest request = new ObjectMapper().readValue("""
				{
				  "plan_day_id": 20,
				  "actual_distance_km": 3.20,
				  "actual_time_sec": 1240,
				  "actual_pace_min_per_km": 6.45
				}
				""", FeedbackRequest.class);
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 360, false, null, LocalDateTime.parse("2026-05-01T09:00:00"));
		when(repository.findPlanDayByIdForUser(20L, 1L)).thenReturn(planDay);
		when(aiCoachingClient.generateFeedback(any())).thenReturn(null);
		when(repository.updateFeedbackForUser(eq(1L), eq(20L), any())).thenReturn(true);

		var response = service.requestFeedback(1L, 20L, request);

		assertThat(request.actualPaceSecPerKm()).isNull();
		assertThat(response.aiFeedbackComment())
				.contains("평균 페이스 6:27/km")
				.contains("목표보다 1.80km 부족");
		verify(aiCoachingClient).generateFeedback(argThat(aiRequest ->
				aiRequest.feedbackRequest().actualPaceSecPerKm().equals(387)
						&& aiRequest.paceDeltaSecPerKm().equals(27)
		));
	}

	@Test
	void requestFeedback_usesAiFeedbackWhenAiClientReturnsComment() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("3.2"), 1240, 387);
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 360, false, null, LocalDateTime.parse("2026-05-01T09:00:00"));
		when(repository.findPlanDayByIdForUser(20L, 1L)).thenReturn(planDay);
		when(aiCoachingClient.generateFeedback(new AiCoachingFeedbackRequest(
				20L,
				LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"),
				360,
				new BigDecimal("-1.80"),
				27,
				request
		)))
				.thenReturn("AI 코치: 목표 대비 안정적인 페이스입니다. 다음 러닝은 회복 강도로 진행하세요.");
		when(repository.updateFeedbackForUser(eq(1L), eq(20L), any())).thenReturn(true);

		var response = service.requestFeedback(1L, 20L, request);

		assertThat(response.aiFeedbackComment()).contains("AI 코치");
		verify(repository).updateFeedbackForUser(1L, 20L, "AI 코치: 목표 대비 안정적인 페이스입니다. 다음 러닝은 회복 강도로 진행하세요.");
	}

	@Test
	void requestFeedback_sendsPlanTargetAndDeltaContextToAiClient() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("3.2"), 1240, 387);
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 360, false, null, LocalDateTime.parse("2026-05-01T09:00:00"));
		when(repository.findPlanDayByIdForUser(20L, 1L)).thenReturn(planDay);
		when(aiCoachingClient.generateFeedback(any())).thenReturn("AI 목표 대비 피드백");
		when(repository.updateFeedbackForUser(eq(1L), eq(20L), any())).thenReturn(true);

		service.requestFeedback(1L, 20L, request);

		verify(aiCoachingClient).generateFeedback(argThat(aiRequest ->
				aiRequest.planDayId().equals(20L)
						&& aiRequest.planDate().equals(LocalDate.parse("2026-05-04"))
						&& aiRequest.targetDistanceKm().compareTo(new BigDecimal("5.00")) == 0
						&& aiRequest.targetPaceSecPerKm().equals(360)
						&& aiRequest.distanceDeltaKm().compareTo(new BigDecimal("-1.80")) == 0
						&& aiRequest.paceDeltaSecPerKm().equals(27)
		));
	}

	@Test
	void requestFeedback_relaxesFuturePlansWhenRecentRecordsMissTargets() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("3.20"), 1240, 432);
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 360, false, null, LocalDateTime.parse("2026-05-01T09:00:00"));
		when(repository.findPlanDayByIdForUser(20L, 1L)).thenReturn(planDay);
		when(repository.updateFeedbackForUser(eq(1L), eq(20L), any())).thenReturn(true);
		when(repository.findRecentPlanPerformances(1L, 10L, 5)).thenReturn(List.of(
				new PlanPerformanceRow(20L, new BigDecimal("5.00"), 360, new BigDecimal("3.20"), 432)
		));
		when(repository.countPlanDaysThrough(10L, LocalDate.parse("2026-05-04"))).thenReturn(2);
		when(repository.countCompletedPlanDaysThrough(10L, LocalDate.parse("2026-05-04"))).thenReturn(1);

		service.requestFeedback(1L, 20L, request);

		verify(repository).adjustFuturePlanTargets(1L, 10L, LocalDate.parse("2026-05-04"), new BigDecimal("0.90"), new BigDecimal("1.05"));
	}

	@Test
	void requestFeedback_progressesFuturePlansWhenAchievementAndRecentRecordsAreStrong() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("5.20"), 1800, 348);
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 360, false, null, LocalDateTime.parse("2026-05-01T09:00:00"));
		when(repository.findPlanDayByIdForUser(20L, 1L)).thenReturn(planDay);
		when(repository.updateFeedbackForUser(eq(1L), eq(20L), any())).thenReturn(true);
		when(repository.findRecentPlanPerformances(1L, 10L, 5)).thenReturn(List.of(
				new PlanPerformanceRow(20L, new BigDecimal("5.00"), 360, new BigDecimal("5.20"), 348)
		));
		when(repository.countPlanDaysThrough(10L, LocalDate.parse("2026-05-04"))).thenReturn(1);
		when(repository.countCompletedPlanDaysThrough(10L, LocalDate.parse("2026-05-04"))).thenReturn(1);

		service.requestFeedback(1L, 20L, request);

		verify(repository).adjustFuturePlanTargets(1L, 10L, LocalDate.parse("2026-05-04"), new BigDecimal("1.05"), new BigDecimal("0.97"));
	}

	@Test
	void completePlanWithRunningRecordReturnsExistingFeedbackWithoutAiCall() {
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 390, true, "Existing running feedback", LocalDateTime.parse("2026-05-04T10:30:00"));
		when(repository.findPlanDayByIdForUser(20L, 1L)).thenReturn(planDay);

		var response = service.completePlanWithRunningRecord(1L, 20L, new BigDecimal("5.00"), 1960, 392);

		assertThat(response.completed()).isTrue();
		assertThat(response.aiFeedbackComment()).isEqualTo("Existing running feedback");
		verify(aiCoachingClient, never()).generateFeedback(any());
		verify(repository, never()).updateFeedbackForUser(eq(1L), eq(20L), any());
		verify(repository, never()).findRecentPlanPerformances(eq(1L), eq(10L), eq(5));
	}

	@Test
	void completePlanWithRunningRecordUsesStoredPaceSecondsForFeedback() {
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 390, false, null, LocalDateTime.parse("2026-05-01T09:00:00"));
		when(repository.findPlanDayByIdForUser(20L, 1L)).thenReturn(planDay);
		when(repository.updateFeedbackForUser(eq(1L), eq(20L), any())).thenReturn(true);

		var response = service.completePlanWithRunningRecord(1L, 20L, new BigDecimal("5.00"), 1960, 392);

		assertThat(response.completed()).isTrue();
		assertThat(response.aiFeedbackComment()).contains("6:32/km");
	}

	@Test
	void lockPlanForRunningRecordDeletionLocksOwnedPlan() {
		PlanDayRow planDay = new PlanDayRow(20L, 1L, 10L, LocalDate.parse("2026-05-04"),
				new BigDecimal("5.00"), 390, true, "feedback", LocalDateTime.parse("2026-05-04T10:30:00"));
		when(repository.findPlanDayByIdForUser(20L, 1L)).thenReturn(planDay);

		service.lockPlanForRunningRecordDeletion(1L, 20L);

		verify(repository).findPlanDayByIdForUser(20L, 1L);
	}

	@Test
	void updateGoalStatus_updatesGoalFlagsAndReturnsCurrentGoalShape() {
		GoalStatusUpdateRequest request = new GoalStatusUpdateRequest(false, true);
		GoalRow completedGoal = new GoalRow(10L, 1L, 4, 3, new BigDecimal("5.00"), 360,
				LocalDateTime.parse("2026-05-01T09:00:00"), false, true,
				LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-29"), List.of("mon", "wed", "fri"));
		when(repository.updateGoalStatusForUser(1L, 10L, false, true)).thenReturn(true);
		when(repository.findGoalByIdForUser(10L, 1L)).thenReturn(completedGoal);
		when(repository.findPlanDaysByGoalId(10L)).thenReturn(List.of());

		var response = service.updateGoalStatus(1L, 10L, request);

		assertThat(response.goalId()).isEqualTo(10L);
		assertThat(response.hasActiveGoal()).isFalse();
		assertThat(response.status()).isEqualTo("completed");
		assertThat(response.goal().active()).isFalse();
		assertThat(response.goal().achieved()).isTrue();
		verify(repository).updateGoalStatusForUser(1L, 10L, false, true);
	}
}
