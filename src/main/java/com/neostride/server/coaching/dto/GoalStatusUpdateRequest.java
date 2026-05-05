package com.neostride.server.coaching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "코칭 목표 상태 변경 요청")
public record GoalStatusUpdateRequest(
		@JsonProperty("is_active") Boolean active,
		@JsonProperty("is_achieved") Boolean achieved
) {
}
