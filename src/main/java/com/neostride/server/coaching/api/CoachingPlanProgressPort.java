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

	void lockPlanForRunningRecordDeletion(long userId, long planDayId);

	void restorePlanToPendingAfterRunningRecordDeleted(long userId, long planDayId);
}
