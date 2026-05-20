package com.neostride.server.coaching.ai;

import com.neostride.server.coaching.dto.FeedbackRequest;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AiCoachingFeedbackRequest(
		Long planDayId,
		LocalDate planDate,
		BigDecimal targetDistanceKm,
		BigDecimal targetPaceMinPerKm,
		BigDecimal distanceDeltaKm,
		BigDecimal paceDeltaMinPerKm,
		FeedbackRequest feedbackRequest
) {
}
