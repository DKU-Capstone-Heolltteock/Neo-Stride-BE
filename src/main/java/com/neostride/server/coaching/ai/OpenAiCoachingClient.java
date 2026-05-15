package com.neostride.server.coaching.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neostride.server.coaching.dto.FeedbackRequest;
import com.neostride.server.coaching.dto.GoalRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCoachingClient implements AiCoachingClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String apiKey;
	private final String model;

	public OpenAiCoachingClient(
			@Value("${neostride.ai.openai.api-key:${OPENAI_API_KEY:}}") String apiKey,
			@Value("${neostride.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${neostride.ai.openai.model:gpt-4o-mini}") String model
	) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model;
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}

	@Override
	public AiCoachingPlan generatePlan(GoalRequest request, int durationWeeks, LocalDate startDate) {
		if (!enabled()) {
			return null;
		}
		try {
			String content = completeJson(planPrompt(request, durationWeeks, startDate));
			return parsePlan(content);
		} catch (RuntimeException exception) {
			return null;
		} catch (Exception exception) {
			return null;
		}
	}

	@Override
	public String generateFeedback(AiCoachingFeedbackRequest request) {
		if (!enabled()) {
			return null;
		}
		try {
			String content = completeJson(feedbackPrompt(request));
			JsonNode json = objectMapper.readTree(content);
			String feedback = json.path("ai_feedback_comment").asText(null);
			return feedback == null || feedback.isBlank() ? null : feedback.trim();
		} catch (RuntimeException exception) {
			return null;
		} catch (Exception exception) {
			return null;
		}
	}

	private boolean enabled() {
		return !apiKey.isBlank();
	}

	private String completeJson(String userPrompt) throws Exception {
		Map<String, Object> body = Map.of(
				"model", model,
				"temperature", 0.3,
				"response_format", Map.of("type", "json_object"),
				"messages", List.of(
						Map.of("role", "system", "content", systemPrompt()),
						Map.of("role", "user", "content", userPrompt)
				)
		);
		JsonNode response = restClient.post()
				.uri("/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + apiKey)
				.body(body)
				.retrieve()
				.body(JsonNode.class);
		if (response == null || response.path("choices").isEmpty()) {
			throw new IllegalStateException("OpenAI 응답이 비어 있습니다.");
		}
		String content = response.path("choices").get(0).path("message").path("content").asText();
		if (content == null || content.isBlank()) {
			throw new IllegalStateException("OpenAI 메시지 content가 비어 있습니다.");
		}
		return content;
	}

	private AiCoachingPlan parsePlan(String content) throws Exception {
		JsonNode json = objectMapper.readTree(content);
		String summary = json.path("summary").asText(null);
		List<AiCoachingPlanDay> planDays = new ArrayList<>();
		for (JsonNode node : json.path("plan_days")) {
			String planDate = node.path("plan_date").asText(null);
			JsonNode distanceNode = node.path("day_distance_km");
			JsonNode paceNode = node.path("day_pace_min_per_km");
			if (planDate == null || distanceNode.isMissingNode() || paceNode.isMissingNode()) {
				continue;
			}
			planDays.add(new AiCoachingPlanDay(
					LocalDate.parse(planDate),
					distanceNode.decimalValue(),
					paceNode.intValue(),
					node.path("description").asText(null)
			));
		}
		return new AiCoachingPlan(summary, planDays);
	}

	private String systemPrompt() {
		return "너는 러닝 코치다. 사용자의 부상 위험과 휴식을 우선한다. "
				+ "초보자에게 과도한 강도나 매일 달리기를 권하지 않는다. "
				+ "반드시 요청받은 JSON 객체만 출력하고 마크다운은 출력하지 않는다.";
	}

	private String planPrompt(GoalRequest request, int durationWeeks, LocalDate startDate) throws Exception {
		return "다음 사용자 입력으로 러닝 플랜을 생성하라. "
				+ "응답 JSON schema: {\"summary\": string, \"plan_days\": [{\"plan_date\": \"yyyy-MM-dd\", \"day_distance_km\": number, \"day_pace_min_per_km\": integer, \"description\": string}]} "
				+ "plan_days는 running_days에 해당하는 날짜만 포함하고, 기간은 start_date부터 duration_weeks 주 이내여야 한다. "
				+ "day_distance_km와 day_pace_min_per_km는 양수여야 한다. "
				+ "사용자 입력: " + objectMapper.writeValueAsString(Map.of(
						"request", request,
						"duration_weeks", durationWeeks,
						"start_date", startDate.toString()
				));
	}

	private String feedbackPrompt(AiCoachingFeedbackRequest request) throws Exception {
		FeedbackRequest feedback = request.feedbackRequest();
		return "러닝 완료 기록에 대한 짧은 코칭 피드백을 생성하라. "
				+ "응답 JSON schema: {\"ai_feedback_comment\": string}. "
				+ "칭찬 1문장, 개선/회복 조언 1문장으로 한국어로 작성하라. "
				+ "입력: " + objectMapper.writeValueAsString(Map.of(
						"plan_day_id", request.planDayId(),
						"actual_distance_km", feedback.actualDistanceKm(),
						"actual_time_sec", feedback.actualTimeSec(),
						"actual_pace_min_per_km", feedback.actualPaceMinPerKm()
				));
	}
}
