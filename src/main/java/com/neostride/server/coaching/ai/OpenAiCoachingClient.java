package com.neostride.server.coaching.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neostride.server.coaching.dto.FeedbackRequest;
import com.neostride.server.coaching.dto.GoalRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCoachingClient implements AiCoachingClient {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiCoachingClient.class);
	private static final int DEFAULT_TIMEOUT_MS = 8_000;
	private static final int DISTANCE_SCALE = 2;

	private final RestClient restClient;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String apiKey;
	private final String model;

	@Autowired
	public OpenAiCoachingClient(
			@Value("${neostride.ai.openai.api-key:${OPENAI_API_KEY:}}") String apiKey,
			@Value("${neostride.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${neostride.ai.openai.model:gpt-5.4-mini}") String model,
			@Value("${neostride.ai.openai.timeout-ms:${OPENAI_TIMEOUT_MS:8000}}") int timeoutMs
	) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model;
		Duration timeout = Duration.ofMillis(Math.max(1, timeoutMs));
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(timeout).build()
		);
		requestFactory.setReadTimeout(timeout);
		this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}

	OpenAiCoachingClient(String apiKey, String baseUrl, String model) {
		this(apiKey, baseUrl, model, DEFAULT_TIMEOUT_MS);
	}

	@Override
	public AiCoachingPlan generatePlan(GoalRequest request, int durationWeeks, LocalDate startDate, List<LocalDate> planDates) {
		if (!enabled()) {
			return null;
		}
		try {
			String content = completeJson(planPrompt(request, durationWeeks, startDate, planDates), planResponseFormat());
			return parsePlanForDatabase(content);
		} catch (RuntimeException exception) {
			logger.warn("OpenAI plan generation failed", exception);
			return null;
		} catch (Exception exception) {
			logger.warn("OpenAI plan generation failed", exception);
			return null;
		}
	}

	@Override
	public String generateFeedback(AiCoachingFeedbackRequest request) {
		if (!enabled()) {
			return null;
		}
		try {
			String content = completeJson(feedbackPrompt(request), feedbackResponseFormat());
			return parseFeedbackForDatabase(content);
		} catch (RuntimeException exception) {
			return null;
		} catch (Exception exception) {
			return null;
		}
	}

	private boolean enabled() {
		return !apiKey.isBlank();
	}

	private String completeJson(String userPrompt, Map<String, Object> responseFormat) throws Exception {
		Map<String, Object> body = Map.of(
				"model", model,
				"temperature", 0.3,
				"response_format", responseFormat,
				"messages", List.of(
						Map.of("role", "system", "content", systemPrompt()),
						Map.of("role", "user", "content", userPrompt)
				)
		);
		String responseBody = restClient.post()
				.uri("/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + apiKey)
				.body(body)
				.retrieve()
				.body(String.class);
		JsonNode response = objectMapper.readTree(responseBody);
		if (response == null || response.path("choices").isEmpty()) {
			throw new IllegalStateException("OpenAI 응답이 비어 있습니다.");
		}
		String content = response.path("choices").get(0).path("message").path("content").asText();
		if (content == null || content.isBlank()) {
			throw new IllegalStateException("OpenAI 메시지 content가 비어 있습니다.");
		}
		return content;
	}

	AiCoachingPlan parsePlanForDatabase(String content) throws Exception {
		JsonNode json = objectMapper.readTree(content);
		String summary = sanitizeText(json.path("summary").asText(null), 500);
		List<AiCoachingPlanDay> planDays = new ArrayList<>();
		for (JsonNode node : json.path("plan_days")) {
			String planDate = node.path("plan_date").asText(null);
			BigDecimal distance = positiveNumber(node.path("day_distance_km"), DISTANCE_SCALE);
			Integer pace = positiveInteger(node.path("day_pace_sec_per_km"));
			String description = sanitizeText(node.path("description").asText(null), 255);
			if (planDate == null || distance == null || pace == null || description == null) {
				continue;
			}
			try {
				planDays.add(new AiCoachingPlanDay(
						LocalDate.parse(planDate),
						distance,
						pace,
						description
				));
			} catch (DateTimeParseException exception) {
				// Skip non-DB-ready dates and let the service fallback if no valid rows remain.
			}
		}
		return new AiCoachingPlan(summary, planDays);
	}

	String parseFeedbackForDatabase(String content) throws Exception {
		JsonNode json = objectMapper.readTree(content);
		return sanitizeText(json.path("ai_feedback_comment").asText(null), 500);
	}

	private BigDecimal positiveNumber(JsonNode node, int scale) {
		if (node == null || node.isNull()) {
			return null;
		}
		try {
			BigDecimal value = node.isNumber()
					? node.decimalValue()
					: new BigDecimal(node.asText("").trim());
			if (value.compareTo(BigDecimal.ZERO) <= 0) {
				return null;
			}
			return value.setScale(scale, RoundingMode.HALF_UP);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private Integer positiveInteger(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		try {
			BigDecimal value = node.isNumber()
					? node.decimalValue()
					: new BigDecimal(node.asText("").trim());
			if (value.compareTo(BigDecimal.ZERO) <= 0 || value.stripTrailingZeros().scale() > 0) {
				return null;
			}
			return value.intValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			return null;
		}
	}

	private String sanitizeText(String value, int maxLength) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isBlank()) {
			return null;
		}
		return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
	}

	String systemPrompt() {
		return "너는 러닝 코치다. 사용자의 부상 위험과 휴식을 우선한다. "
				+ "초보자에게 과도한 강도, 매일 달리기, 급격한 거리 증가를 권하지 않는다. "
				+ "의료 진단, 치료 지시, 약물 권고를 하지 않는다. 통증, 어지러움, 흉통, 호흡곤란이 있으면 즉시 운동을 중단하고 전문가 상담을 권한다. "
				+ "반드시 요청받은 한국어 JSON 객체만 출력하고 마크다운은 출력하지 않는다. "
				+ "스키마에 없는 필드와 null을 출력하지 않는다.";
	}

	Map<String, Object> planResponseFormat() {
		return Map.of(
				"type", "json_schema",
				"json_schema", Map.of(
						"name", "coaching_plan",
						"strict", true,
						"schema", Map.of(
								"type", "object",
								"additionalProperties", false,
								"required", List.of("summary", "plan_days"),
								"properties", Map.of(
										"summary", Map.of("type", "string", "maxLength", 500),
										"plan_days", Map.of(
												"type", "array",
												"items", Map.of(
														"type", "object",
														"additionalProperties", false,
														"required", List.of("plan_date", "day_distance_km", "day_pace_sec_per_km", "description"),
														"properties", Map.of(
																"plan_date", Map.of("type", "string", "pattern", "^\\d{4}-\\d{2}-\\d{2}$"),
																"day_distance_km", Map.of("type", "number", "exclusiveMinimum", 0),
																"day_pace_sec_per_km", Map.of("type", "integer", "exclusiveMinimum", 0),
																"description", Map.of("type", "string", "maxLength", 255)
														)
												)
										)
								)
						)
				)
		);
	}

	Map<String, Object> feedbackResponseFormat() {
		return Map.of(
				"type", "json_schema",
				"json_schema", Map.of(
						"name", "coaching_feedback",
						"strict", true,
						"schema", Map.of(
								"type", "object",
								"additionalProperties", false,
								"required", List.of("ai_feedback_comment"),
								"properties", Map.of(
										"ai_feedback_comment", Map.of("type", "string", "maxLength", 500)
								)
						)
				)
		);
	}

	String planPrompt(GoalRequest request, int durationWeeks, LocalDate startDate, List<LocalDate> planDates) throws Exception {
		List<String> expectedPlanDates = planDates == null ? List.of() : planDates.stream()
				.map(LocalDate::toString)
				.toList();
		return "다음 사용자 입력으로 러닝 플랜을 생성하라. "
				+ "응답 JSON schema: {\"summary\": string, \"plan_days\": [{\"plan_date\": \"yyyy-MM-dd\", \"day_distance_km\": number, \"day_pace_sec_per_km\": integer, \"description\": string}]} "
				+ "goal_distance_km와 goal_pace_sec_per_km는 사용자의 최종 목표이므로 그대로 보존하고, 각 plan_days의 day_distance_km와 day_pace_sec_per_km는 해당 날짜의 일일 미션으로 점진적으로 산출한다. 초반은 최종 목표보다 낮은 거리와 느린 페이스로 시작하고 마지막 주에 최종 목표에 근접하게 조정한다. "
				+ "DB 저장 제약: plan_days는 plan_dates와 같은 길이와 같은 순서로만 출력한다. plan_date는 반드시 plan_dates의 값을 그대로 사용하고, plan_dates에 없는 날짜를 추가하지 않는다. "
				+ "plan_date는 yyyy-MM-dd 형식, day_distance_km는 0보다 큰 숫자이며 소수점 2자리 이하, day_pace_sec_per_km는 0보다 큰 정수 seconds/km로 출력한다. "
				+ "summary는 한국어 500자 이하, description은 한국어 255자 이하로 출력한다. null을 출력하지 않는다. "
				+ "사용자 입력: " + objectMapper.writeValueAsString(Map.of(
						"request", request,
						"duration_weeks", durationWeeks,
						"start_date", startDate.toString(),
						"plan_dates", expectedPlanDates
				));
	}

	private String feedbackPrompt(AiCoachingFeedbackRequest request) throws Exception {
		FeedbackRequest feedback = request.feedbackRequest();
		return "러닝 완료 기록에 대한 짧은 코칭 피드백을 생성하라. "
				+ "응답 JSON schema: {\"ai_feedback_comment\": string}. "
				+ "DB 저장 제약: ai_feedback_comment는 한국어 500자 이하 문자열이며 null을 출력하지 않는다. "
				+ "칭찬 1문장, 개선/회복 조언 1문장으로 작성하라. 의료 진단, 치료 지시, 약물 권고는 하지 않는다. "
				+ "통증, 어지러움, 흉통, 호흡곤란이 있으면 운동 중단과 전문가 상담을 권한다. "
				+ "목표 대비 차이는 actual - target 기준이며, pace_delta_sec_per_km가 양수면 목표보다 느린 것이다. "
				+ "입력: " + objectMapper.writeValueAsString(Map.of(
						"plan_day_id", request.planDayId(),
						"plan_date", request.planDate().toString(),
						"target_distance_km", request.targetDistanceKm(),
						"target_pace_sec_per_km", request.targetPaceSecPerKm(),
						"actual_distance_km", feedback.actualDistanceKm(),
						"actual_time_sec", feedback.actualTimeSec(),
						"actual_pace_sec_per_km", feedback.actualPaceSecPerKm(),
						"distance_delta_km", request.distanceDeltaKm(),
						"pace_delta_sec_per_km", request.paceDeltaSecPerKm()
				));
	}
}
