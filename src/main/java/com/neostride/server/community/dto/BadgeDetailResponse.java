package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record BadgeDetailResponse(
		@JsonProperty("Badge") String tier,
		@JsonProperty("record_id") Long recordId,
		BigDecimal distance,
		String pace,
		@JsonProperty("achieved_at") String achievedAt
) {}
