package com.neostride.server.coaching.ai;

import com.neostride.server.coaching.dto.FeedbackRequest;

public record AiCoachingFeedbackRequest(
		Long planDayId,
		FeedbackRequest feedbackRequest
) {
}
