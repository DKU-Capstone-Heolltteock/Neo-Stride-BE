package com.neostride.server.coaching.service;

import com.neostride.server.coaching.dto.FeedbackRequest;
import com.neostride.server.coaching.dto.GoalRequest;
import com.neostride.server.coaching.repository.CoachingRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoachingServiceTest {

	private final CoachingRepository repository = mock(CoachingRepository.class);
	private final CoachingService service = new CoachingService(repository);

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
}
