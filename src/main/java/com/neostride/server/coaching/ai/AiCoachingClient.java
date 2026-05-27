package com.neostride.server.coaching.ai;

import com.neostride.server.coaching.dto.GoalRequest;
import java.time.LocalDate;
import java.util.List;

public interface AiCoachingClient {

	AiCoachingPlan generatePlan(GoalRequest request, int durationWeeks, LocalDate startDate, List<LocalDate> planDates);

	String generateFeedback(AiCoachingFeedbackRequest request);
}
