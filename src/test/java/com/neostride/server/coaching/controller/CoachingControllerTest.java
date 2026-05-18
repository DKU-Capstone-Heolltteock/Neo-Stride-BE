package com.neostride.server.coaching.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.coaching.dto.FeedbackRequest;
import com.neostride.server.coaching.dto.FeedbackResponse;
import com.neostride.server.coaching.dto.GoalRequest;
import com.neostride.server.coaching.dto.GoalResponse;
import com.neostride.server.coaching.dto.GoalStatusUpdateRequest;
import com.neostride.server.coaching.dto.TodayPlanResponse;
import com.neostride.server.coaching.service.CoachingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoachingControllerTest {

	private static final String AUTHORIZATION = "Bearer access-token";

	private final CoachingService service = mock(CoachingService.class);
	private final AuthenticatedUserService authenticatedUserService = mock(AuthenticatedUserService.class);
	private final CoachingController controller = new CoachingController(service, authenticatedUserService);

	@Test
	void createGoal_returnsCreatedResponse() {
		GoalRequest request = new GoalRequest(1L, "1month", 0, List.of("mon", "wed", "fri"), new BigDecimal("5.0"), new BigDecimal("6.5"), "2026-04-30");
		GoalResponse responseBody = GoalResponse.of(10L, true, "active", null, List.of());
		authenticate();
		when(service.createGoal(request)).thenReturn(responseBody);

		var response = controller.createGoal(AUTHORIZATION, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isSameAs(responseBody);
	}

	@Test
	void getActiveGoal_returnsOkResponse() {
		GoalResponse responseBody = GoalResponse.of(10L, true, "active", null, List.of());
		authenticate();
		when(service.getActiveGoal(1L)).thenReturn(responseBody);

		var response = controller.getActiveGoal(AUTHORIZATION, 1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(responseBody);
	}

	@Test
	void getTodayPlan_returnsOkResponse() {
		TodayPlanResponse responseBody = new TodayPlanResponse(false, null, null);
		authenticate();
		when(service.getTodayPlan(1L)).thenReturn(responseBody);

		var response = controller.getTodayPlan(AUTHORIZATION, 1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(responseBody);
	}

	@Test
	void requestFeedback_returnsOkResponse() {
		FeedbackRequest request = new FeedbackRequest(20L, new BigDecimal("3.2"), 1240, new BigDecimal("6.45"));
		FeedbackResponse responseBody = new FeedbackResponse(20L, true, "목표보다 안정적인 페이스로 완주했습니다.", "2026-05-05T20:30:00");
		authenticate();
		when(service.requestFeedback(1L, 20L, request)).thenReturn(responseBody);

		var response = controller.requestFeedback(AUTHORIZATION, 20L, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(responseBody);
	}

	@Test
	void deleteGoal_returnsOkMap() {
		authenticate();
		when(service.deleteGoal(1L, 10L)).thenReturn(Map.of("status", "success", "message", "삭제 완료"));

		var response = controller.deleteGoal(AUTHORIZATION, 10L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("status", "success");
	}

	@Test
	void updateGoalStatus_returnsUpdatedGoal() {
		GoalStatusUpdateRequest request = new GoalStatusUpdateRequest(false, true);
		GoalResponse responseBody = GoalResponse.of(10L, false, "completed", null, List.of());
		authenticate();
		when(service.updateGoalStatus(1L, 10L, request)).thenReturn(responseBody);

		var response = controller.updateGoalStatus(AUTHORIZATION, 10L, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(responseBody);
	}

	private void authenticate() {
		when(authenticatedUserService.requireUserId(AUTHORIZATION)).thenReturn(1L);
	}
}
