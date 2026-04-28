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

		@Schema(description = "평균 페이스. 단위: minutes/km", example = "6.36", minimum = "0")
		@JsonProperty("pace")
		BigDecimal pace,

		@Schema(description = "소모 칼로리. 단위: kcal", example = "235.69", minimum = "0")
		@JsonProperty("calories")
		BigDecimal calories,

		@ArraySchema(schema = @Schema(implementation = GpsTraceRequest.class), minItems = 1)
		@JsonProperty("gps_traces")
		List<GpsTraceRequest> gpsTraces
) {
}
