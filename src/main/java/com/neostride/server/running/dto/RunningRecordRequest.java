package com.neostride.server.running.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "러닝 기록 저장 요청")
public record RunningRecordRequest(
		@Schema(description = "사용자 고유 ID", example = "1", minimum = "1")
		@JsonProperty("user_id")
		Long userId,

		@Schema(description = "연동된 코칭 플랜 ID. 없으면 null", example = "null", nullable = true)
		@JsonProperty("plan_id")
		Long planId,

		@Schema(description = "총 주행 거리", example = "3.25", minimum = "0")
		@JsonProperty("total_distance")
		BigDecimal totalDistance,

		@Schema(description = "총 주행 시간. 단위: seconds", example = "1240.0", minimum = "0")
		@JsonProperty("duration")
		BigDecimal duration,

		@Schema(description = "평균 페이스. 단위: seconds/km. 구버전 minutes/km decimal(<60)도 수신 시 seconds/km로 변환", example = "342", minimum = "0")
		@JsonProperty("pace")
		BigDecimal pace,

		@Schema(description = "소모 칼로리. 단위: kcal", example = "235.69", minimum = "0")
		@JsonProperty("calories")
		BigDecimal calories,

		@Schema(description = "경로 상세 설명", example = "", nullable = true)
		@JsonProperty("route_detail")
		String routeDetail,

		@ArraySchema(schema = @Schema(implementation = GpsTraceRequest.class), minItems = 1)
		@JsonProperty("gps_traces")
		List<GpsTraceRequest> gpsTraces,

		@Schema(description = "러닝 결과로 산출된 배지 등급", example = "GOLD", nullable = true)
		@JsonProperty("badge")
		String badge
) {
	public RunningRecordRequest(Long userId, Long planId, BigDecimal totalDistance, BigDecimal duration, BigDecimal pace,
						  BigDecimal calories, String routeDetail, List<GpsTraceRequest> gpsTraces) {
		this(userId, planId, totalDistance, duration, pace, calories, routeDetail, gpsTraces, null);
	}
}
