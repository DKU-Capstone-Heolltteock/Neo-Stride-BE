package com.neostride.server.coaching.api;

import java.math.BigDecimal;

public interface CoachingPlanProgressPort {
	void completePlanWithRunningRecord(
			long userId,
			long planDayId,
			BigDecimal actualDistanceKm,
			Integer actualTimeSec,
			Integer actualPaceSecPerKm
	);

	void restorePlanToPendingAfterRunningRecordDeleted(long userId, long planDayId);
}
