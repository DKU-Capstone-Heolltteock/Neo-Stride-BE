package com.neostride.server.coaching.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCoachingClientTest {

	private final OpenAiCoachingClient client = new OpenAiCoachingClient("", "https://api.openai.com/v1", "gpt-5.4-mini");

	@Test
	void planResponseFormat_usesStrictJsonSchemaForDbReadyFields() {
		Map<String, Object> responseFormat = client.planResponseFormat();

		assertThat(responseFormat).containsEntry("type", "json_schema");
		Map<String, Object> jsonSchema = asMap(responseFormat.get("json_schema"));
		assertThat(jsonSchema).containsEntry("strict", true);
		Map<String, Object> schema = asMap(jsonSchema.get("schema"));
		assertThat(schema).containsEntry("additionalProperties", false);
		assertThat(schema.get("required")).isEqualTo(List.of("summary", "plan_days"));

		Map<String, Object> properties = asMap(schema.get("properties"));
		Map<String, Object> planDays = asMap(properties.get("plan_days"));
		Map<String, Object> itemSchema = asMap(planDays.get("items"));
		assertThat(itemSchema).containsEntry("additionalProperties", false);
		assertThat(itemSchema.get("required")).isEqualTo(List.of("plan_date", "day_distance_km", "day_pace_sec_per_km", "description"));
		Map<String, Object> itemProperties = asMap(itemSchema.get("properties"));
		assertThat(asMap(itemProperties.get("plan_date"))).containsEntry("pattern", "^\\d{4}-\\d{2}-\\d{2}$");
		assertThat(asMap(itemProperties.get("day_pace_sec_per_km"))).containsEntry("type", "integer");
	}

	@Test
	void feedbackResponseFormat_usesStrictJsonSchemaForDbReadyComment() {
		Map<String, Object> responseFormat = client.feedbackResponseFormat();

		assertThat(responseFormat).containsEntry("type", "json_schema");
		Map<String, Object> jsonSchema = asMap(responseFormat.get("json_schema"));
		assertThat(jsonSchema).containsEntry("strict", true);
		Map<String, Object> schema = asMap(jsonSchema.get("schema"));
		assertThat(schema).containsEntry("additionalProperties", false);
		assertThat(schema.get("required")).isEqualTo(List.of("ai_feedback_comment"));
	}

	@Test
	void parsePlanForDatabase_rejectsMissingRequiredFieldsAndNonPositiveNumbers() throws Exception {
		String content = """
				{
				  "summary": "AI 플랜",
				  "plan_days": [
				    {"plan_date": "2026-05-04", "day_distance_km": "2.555", "day_pace_sec_per_km": 435.0, "description": "회복 조깅"},
				    {"plan_date": "2026-05-06", "day_distance_km": 0, "day_pace_sec_per_km": 390, "description": "잘못된 거리"},
				    {"plan_date": "2026-05-08", "day_distance_km": 3.0, "description": "페이스 누락"},
				    {"plan_date": "2026-05-11", "day_distance_km": 3.0, "day_pace_sec_per_km": 390.5, "description": "정수가 아닌 페이스"}
				  ]
				}
				""";

		AiCoachingPlan plan = client.parsePlanForDatabase(content);

		assertThat(plan.summary()).isEqualTo("AI 플랜");
		assertThat(plan.planDays()).hasSize(1);
		assertThat(plan.planDays().getFirst().dayDistanceKm()).isEqualByComparingTo(new BigDecimal("2.56"));
		assertThat(plan.planDays().getFirst().dayPaceSecPerKm()).isEqualTo(435);
	}

	@Test
	void parseFeedbackForDatabase_rejectsBlankAndTruncatesOverlongComment() throws Exception {
		assertThat(client.parseFeedbackForDatabase("{\"ai_feedback_comment\": \"   \"}")).isNull();

		String longComment = "좋".repeat(650);
		String parsed = client.parseFeedbackForDatabase("{\"ai_feedback_comment\": \"" + longComment + "\"}");

		assertThat(parsed).hasSize(500);
	}

	@Test
	void generateFeedback_readsOpenAiHttpJsonResponseAndParsesMessageContent() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/chat/completions", this::respondWithOpenAiFeedback);
		server.start();
		try {
			OpenAiCoachingClient httpClient = new OpenAiCoachingClient(
					"test-api-key",
					"http://127.0.0.1:" + server.getAddress().getPort(),
					"test-model"
			);

			String feedback = httpClient.generateFeedback(new AiCoachingFeedbackRequest(
					1L,
					java.time.LocalDate.parse("2026-05-04"),
					new BigDecimal("5.00"),
					360,
					new BigDecimal("-1.80"),
					27,
					new com.neostride.server.coaching.dto.FeedbackRequest(1L, new BigDecimal("3.20"), 1240, 387)
			));

			assertThat(feedback).isEqualTo("좋은 완주입니다. 다음에는 회복을 충분히 챙기세요.");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void promptsTellModelToOutputOnlyDbReadyKoreanJson() throws Exception {
		String systemPrompt = client.systemPrompt();
		String planPrompt = client.planPrompt(
				new com.neostride.server.coaching.dto.GoalRequest(1L, "custom", 1, List.of("mon"), new BigDecimal("5.00"), 390, "2026-05-04"),
				1,
				java.time.LocalDate.parse("2026-05-04"),
				List.of(java.time.LocalDate.parse("2026-05-04"))
		);

		assertThat(systemPrompt)
				.contains("JSON 객체만")
				.contains("스키마에 없는 필드")
				.contains("의료 진단")
				.contains("부상 위험");
		assertThat(planPrompt)
				.contains("DB 저장 제약")
				.contains("plan_dates와 같은 길이와 같은 순서")
				.contains("점진적으로 산출")
				.contains("day_distance_km는 0보다 큰 숫자이며 소수점 2자리 이하")
				.contains("day_pace_sec_per_km는 0보다 큰 정수 seconds/km")
				.contains("null을 출력하지 않는다");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> asMap(Object value) {
		return (Map<String, Object>) value;
	}

	private void respondWithOpenAiFeedback(HttpExchange exchange) throws IOException {
		byte[] response = """
				{
				  "choices": [
				    {
				      "message": {
				        "content": "{\\\"ai_feedback_comment\\\":\\\"좋은 완주입니다. 다음에는 회복을 충분히 챙기세요.\\\"}"
				      }
				    }
				  ]
				}
				""".getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(200, response.length);
		exchange.getResponseBody().write(response);
		exchange.close();
	}
}
