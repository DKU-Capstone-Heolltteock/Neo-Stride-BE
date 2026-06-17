package com.neostride.server.coaching.service;

import com.neostride.server.coaching.api.CoachingPlanProgressPort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CoachingPlanProgressAdapter implements CoachingPlanProgressPort {
	private final CoachingService coachingService;

	public CoachingPlanProgressAdapter(CoachingService coachingService) {
		this.coachingService = coachingService;
	}

	@Override
	public void completePlanWithRunningRecord(long userId, long planDayId, BigDecimal actualDistanceKm,
			Integer actualTimeSec, Integer actualPaceSecPerKm) {
		coachingService.completePlanWithRunningRecord(userId, planDayId, actualDistanceKm, actualTimeSec, actualPaceSecPerKm);
	}

	@Override
	public void restorePlanToPendingAfterRunningRecordDeleted(long userId, long planDayId) {
		coachingService.restorePlanToPendingAfterRunningRecordDeleted(userId, planDayId);
	}
}
