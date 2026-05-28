package com.neostride.server.coaching.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Schema(description = "AI 피드백 요청")
public record FeedbackRequest(
		@JsonProperty("plan_day_id") Long planDayId,
		@JsonProperty("actual_distance_km") BigDecimal actualDistanceKm,
		@JsonProperty("actual_time_sec") Integer actualTimeSec,
		@JsonProperty("actual_pace_sec_per_km") Integer actualPaceSecPerKm,
		@JsonProperty("actual_pace_min_per_km") BigDecimal actualPaceMinPerKm
) {
	public FeedbackRequest(Long planDayId, BigDecimal actualDistanceKm, Integer actualTimeSec,
			Integer actualPaceSecPerKm) {
		this(planDayId, actualDistanceKm, actualTimeSec, actualPaceSecPerKm, null);
	}

	@JsonIgnore
	public Integer resolvedActualPaceSecPerKm() {
		if (actualPaceSecPerKm != null) {
			return actualPaceSecPerKm;
		}
		if (actualPaceMinPerKm == null) {
			return null;
		}
		return actualPaceMinPerKm.multiply(BigDecimal.valueOf(60))
				.setScale(0, RoundingMode.HALF_UP)
				.intValue();
	}

	@JsonIgnore
	public FeedbackRequest withResolvedActualPaceSecPerKm() {
		Integer resolved = resolvedActualPaceSecPerKm();
		if (resolved == null || resolved.equals(actualPaceSecPerKm)) {
			return this;
		}
		return new FeedbackRequest(planDayId, actualDistanceKm, actualTimeSec, resolved, actualPaceMinPerKm);
	}
}
