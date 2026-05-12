package com.neostride.server.running.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "러닝 중 기록된 GPS 좌표")
public record GpsTraceRequest(
		@Schema(description = "위도", example = "37.5665", minimum = "-90", maximum = "90")
		@JsonProperty("latitude")
		Double latitude,

		@Schema(description = "경도", example = "126.978", minimum = "-180", maximum = "180")
		@JsonProperty("longitude")
		Double longitude,

		@Schema(description = "좌표 기록 시각", example = "2026-04-28 09:30:12", pattern = "yyyy-MM-dd HH:mm:ss")
		@JsonProperty("time")
		String time,

		@Schema(description = "트레이스 시점 심박수. 단위: bpm", example = "150.0", nullable = true)
		@JsonProperty("heart_rate")
		Double heartRate,

		@Schema(description = "트레이스 시점 케이던스. 단위: spm", example = "171.0", nullable = true)
		@JsonProperty("cadence")
		Double cadence
) {
}
