package com.neostride.server.coaching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 피드백 응답")
public record FeedbackResponse(
		@JsonProperty("plan_day_id") Long planDayId,
		@JsonProperty("is_completed") boolean completed,
		@JsonProperty("ai_feedback_comment") String aiFeedbackComment,
		@JsonProperty("ai_feedback_at") String aiFeedbackAt
) {
}
