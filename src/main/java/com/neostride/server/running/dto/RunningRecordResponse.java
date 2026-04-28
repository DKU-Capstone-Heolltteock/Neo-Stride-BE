package com.neostride.server.running.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "러닝 기록 저장 응답")
public record RunningRecordResponse(
		@Schema(description = "처리 결과", example = "success", allowableValues = {"success", "error"})
		@JsonProperty("status")
		String status,

		@Schema(description = "처리 메시지", example = "러닝 기록이 저장되었습니다.")
		@JsonProperty("message")
		String message,

		@Schema(description = "저장된 러닝 기록 ID", example = "10")
		@JsonProperty("run_record_id")
		long runRecordId
) {
	public static RunningRecordResponse success(String message, long runRecordId) {
		return new RunningRecordResponse("success", message, runRecordId);
	}

	public static RunningRecordResponse error(String message) {
		return new RunningRecordResponse("error", message, 0);
	}
}
