package com.neostride.server.coaching.controller;

import com.neostride.server.coaching.dto.FeedbackRequest;
import com.neostride.server.coaching.dto.FeedbackResponse;
import com.neostride.server.coaching.dto.GoalRequest;
import com.neostride.server.coaching.dto.GoalResponse;
import com.neostride.server.coaching.dto.TodayPlanResponse;
import com.neostride.server.coaching.service.CoachingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Coaching", description = "코칭/AI 플랜 API")
@RestController
@RequestMapping("/api/coaching")
public class CoachingController {

	private final CoachingService coachingService;

	public CoachingController(CoachingService coachingService) {
		this.coachingService = coachingService;
	}

	@Operation(summary = "코칭 목표 생성", description = "사용자의 코칭 목표를 생성하고 날짜별 플랜을 생성합니다.")
	@PostMapping("/goals")
	public ResponseEntity<GoalResponse> createGoal(@RequestBody GoalRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(coachingService.createGoal(request));
	}

	@Operation(summary = "현재 활성 목표 조회", description = "사용자의 현재 활성 목표와 플랜 목록을 조회합니다.")
	@GetMapping("/goals/active")
	public ResponseEntity<GoalResponse> getActiveGoal(@RequestParam("user_id") long userId) {
		return ResponseEntity.ok(coachingService.getActiveGoal(userId));
	}

	@Operation(summary = "오늘 플랜 조회", description = "사용자의 오늘 코칭 플랜을 조회합니다.")
	@GetMapping("/plans/today")
	public ResponseEntity<TodayPlanResponse> getTodayPlan(@RequestParam("user_id") long userId) {
		return ResponseEntity.ok(coachingService.getTodayPlan(userId));
	}

	@Operation(summary = "AI 피드백 요청", description = "러닝 완료 후 플랜 일자에 피드백을 저장합니다.")
	@PostMapping("/plans/{plan_day_id}/feedback")
	public ResponseEntity<FeedbackResponse> requestFeedback(
			@PathVariable("plan_day_id") long planDayId,
			@RequestBody FeedbackRequest request
	) {
		return ResponseEntity.ok(coachingService.requestFeedback(planDayId, request));
	}

	@Operation(summary = "코칭 목표 삭제", description = "목표를 비활성화합니다.")
	@DeleteMapping("/goals/{goal_id}")
	public ResponseEntity<Map<String, String>> deleteGoal(@PathVariable("goal_id") long goalId) {
		return ResponseEntity.ok(coachingService.deleteGoal(goalId));
	}
}
