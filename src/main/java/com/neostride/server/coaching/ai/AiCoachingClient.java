package com.neostride.server.coaching.ai;

import com.neostride.server.coaching.dto.GoalRequest;
import java.time.LocalDate;

public interface AiCoachingClient {

	AiCoachingPlan generatePlan(GoalRequest request, int durationWeeks, LocalDate startDate);

	String generateFeedback(AiCoachingFeedbackRequest request);
}
