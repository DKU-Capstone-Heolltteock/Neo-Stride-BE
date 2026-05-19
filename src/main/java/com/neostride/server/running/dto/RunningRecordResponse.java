package com.neostride.server.running.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "러닝 기록 응답")
public record RunningRecordResponse(
		@Schema(description = "처리 결과", example = "success", allowableValues = {"success", "error"})
		@JsonProperty("status")
		String status,

		@Schema(description = "처리 메시지", example = "러닝 기록이 저장되었습니다.")
		@JsonProperty("message")
		String message,

		@Schema(description = "러닝 기록 ID", example = "10")
		@JsonProperty("run_record_id")
		Long runRecordId,

		@Schema(description = "기록 생성 시각", example = "2026-04-28T14:30:00")
		@JsonProperty("created_at")
		String createdAt,

		@Schema(description = "총 주행 거리. 단위: km", example = "3.25")
		@JsonProperty("total_distance")
		BigDecimal totalDistance,

		@Schema(description = "총 주행 시간. 단위: seconds", example = "1240")
		@JsonProperty("duration")
		Integer duration,

		@Schema(description = "평균 페이스. 단위: minutes/km, 소수점 아래 두 자리는 초 단위(예: 5.77 = 5분 77초가 아니라 기존 앱 표기 5.77)", example = "5.77")
		@JsonProperty("pace")
		BigDecimal pace,

		@Schema(description = "소모 칼로리. 단위: kcal", example = "235")
		@JsonProperty("calories")
		Integer calories,

		@Schema(description = "GPS 경로 좌표 목록")
		@JsonProperty("gps_traces")
		List<GpsTraceRequest> gpsTraces,

		@Schema(description = "구간별 페이스 목록")
		@JsonProperty("segment_paces")
		List<BigDecimal> segmentPaces
) {
	public static RunningRecordResponse success(String message, long runRecordId) {
		return new RunningRecordResponse("success", message, runRecordId, null, null, null, null, null, null, null);
	}

	public static RunningRecordResponse error(String message) {
		return new RunningRecordResponse("error", message, 0L, null, null, null, null, null, null, null);
	}

	public static RunningRecordResponse record(long runRecordId, String createdAt, BigDecimal totalDistance,
			Integer duration, BigDecimal pace, Integer calories,
			List<GpsTraceRequest> gpsTraces, List<BigDecimal> segmentPaces) {
		return new RunningRecordResponse(null, null, runRecordId, createdAt, totalDistance, duration, pace, calories, gpsTraces, segmentPaces);
	}

	public static RunningRecordResponse record(long runRecordId, String createdAt, String totalDistance,
			Integer duration, BigDecimal pace, Integer calories,
			List<GpsTraceRequest> gpsTraces, List<BigDecimal> segmentPaces) {
		return record(runRecordId, createdAt, new BigDecimal(totalDistance), duration, pace, calories, gpsTraces, segmentPaces);
	}
}
