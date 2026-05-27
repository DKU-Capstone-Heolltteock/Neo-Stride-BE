package com.neostride.server.coaching.ai;

import com.neostride.server.coaching.dto.FeedbackRequest;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AiCoachingFeedbackRequest(
		Long planDayId,
		LocalDate planDate,
		BigDecimal targetDistanceKm,
		Integer targetPaceSecPerKm,
		BigDecimal distanceDeltaKm,
		Integer paceDeltaSecPerKm,
		FeedbackRequest feedbackRequest
) {
}
