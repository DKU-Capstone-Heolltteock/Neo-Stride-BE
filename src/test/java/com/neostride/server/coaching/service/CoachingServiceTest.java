package com.neostride.server.coaching.service;

import com.neostride.server.coaching.ai.AiCoachingClient;
import com.neostride.server.coaching.ai.AiCoachingFeedbackRequest;
import com.neostride.server.coaching.ai.AiCoachingPlan;
import com.neostride.server.coaching.ai.AiCoachingPlanDay;
import com.neostride.server.coaching.dto.FeedbackRequest;
import com.neostride.server.coaching.dto.GoalRequest;
import com.neostride.server.coaching.dto.GoalStatusUpdateRequest;
import com.neostride.server.coaching.repository.CoachingRepository;
import com.neostride.server.coaching.repository.GoalRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoachingServiceTest {

	private final CoachingRepository repository = mock(CoachingRepository.class);
	private final AiCoachingClient aiCoachingClient = mock(AiCoachingClient.class);
	private final CoachingService service = new CoachingService(repository, aiCoachingClient);

	@Test
	void createGoal_insertsGoalAndGeneratedPlanDays() {
		GoalRequest request = new GoalRequest(1L, "1month", 0, List.of("mon", "wed", "fri"), new BigDecimal("5.0"), new BigDecimal("6.5"), "2026-04-30");
		when(repository.insertGoal(any())).thenReturn(10L);
		when(repository.findActiveGoalByUserId(1L)).thenReturn(null);
		when(repository.findPlanDaysByGoalId(10L)).thenReturn(List.of());

		var response = service.createGoal(request);

		assertThat(response.goalId()).isEqualTo(10L);
		assertThat(response.hasActiveGoal()).isTrue();
		assertThat(response.status()).isEqualTo("active");
		verify(repository).deactivateActiveGoals(1L);
		verify(repository).insertPlanDays(eq(1L), eq(10L), any());
	}

	@Test
	void createGoal_usesAiGeneratedPlanDaysWhenAiClientReturnsPlan() {
		GoalRequest request = new GoalRequest(1L, "custom", 1, List.of("mon", "wed"), new BigDecimal("5.0"), new BigDecimal("6.5"), "2026-05-04");
		when(aiCoachingClient.generatePlan(request, 1, LocalDate.parse("2026-05-04")))
				.thenReturn(new AiCoachingPlan("AI 맞춤 1주 플랜", List.of(
						new AiCoachingPlanDay(LocalDate.parse("2026-05-04"), new BigDecimal("2.50"), 7, "회복 조깅"),
						new AiCoachingPlanDay(LocalDate.parse("2026-05-06"), new BigDecimal("3.00"), 6, "가벼운 지속주")
				)));
		when(repository.insertGoal(any())).thenReturn(10L);
		when(repository.findPlanDaysByGoalId(10L)).thenReturn(List.of());

		service.createGoal(request);

		verify(repository).insertPlanDays(eq(1L), eq(10L), argThat(commands ->
				commands.size() == 2
						&& commands.get(0).planDate().equals(LocalDate.parse("2026-05-04"))
						&& commands.get(0).targetDistance().compareTo(new BigDecimal("2.50")) == 0
						&& commands.get(0).targetPace().equals(7)
						&& commands.get(1).planDate().equals(LocalDate.parse("2026-05-06"))
		));
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
		FeedbackRequest request = new FeedbackRequest(21L, new BigDecimal("3.2"), 1240, new BigDecimal("6.45"));

		assertThatThrownBy(() -> service.requestFeedback(20L, request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("plan_day_id");
	}

	@Test
	void requestFeedback_marksPlanCompletedAndReturnsFeedback() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("3.2"), 1240, new BigDecimal("6.45"));
		when(repository.updateFeedback(eq(20L), any())).thenReturn(true);

		var response = service.requestFeedback(20L, request);

		assertThat(response.planDayId()).isEqualTo(20L);
		assertThat(response.completed()).isTrue();
		assertThat(response.aiFeedbackComment()).isNotBlank();
	}

	@Test
	void requestFeedback_usesAiFeedbackWhenAiClientReturnsComment() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("3.2"), 1240, new BigDecimal("6.45"));
		when(aiCoachingClient.generateFeedback(new AiCoachingFeedbackRequest(20L, request)))
				.thenReturn("AI 코치: 목표 대비 안정적인 페이스입니다. 다음 러닝은 회복 강도로 진행하세요.");
		when(repository.updateFeedback(eq(20L), any())).thenReturn(true);

		var response = service.requestFeedback(20L, request);

		assertThat(response.aiFeedbackComment()).contains("AI 코치");
		verify(repository).updateFeedback(20L, "AI 코치: 목표 대비 안정적인 페이스입니다. 다음 러닝은 회복 강도로 진행하세요.");
	}

	@Test
	void updateGoalStatus_updatesGoalFlagsAndReturnsCurrentGoalShape() {
		GoalStatusUpdateRequest request = new GoalStatusUpdateRequest(false, true);
		GoalRow completedGoal = new GoalRow(10L, 1L, 4, 3, new BigDecimal("5.00"), 6,
				LocalDateTime.parse("2026-05-01T09:00:00"), false, true,
				LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-29"), List.of("mon", "wed", "fri"));
		when(repository.updateGoalStatus(10L, false, true)).thenReturn(true);
		when(repository.findGoalById(10L)).thenReturn(completedGoal);
		when(repository.findPlanDaysByGoalId(10L)).thenReturn(List.of());

		var response = service.updateGoalStatus(10L, request);

		assertThat(response.goalId()).isEqualTo(10L);
		assertThat(response.hasActiveGoal()).isFalse();
		assertThat(response.status()).isEqualTo("completed");
		assertThat(response.goal().active()).isFalse();
		assertThat(response.goal().achieved()).isTrue();
		verify(repository).updateGoalStatus(10L, false, true);
	}
}
