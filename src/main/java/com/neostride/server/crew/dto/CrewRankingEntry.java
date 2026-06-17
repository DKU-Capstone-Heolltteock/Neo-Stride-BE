package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record CrewRankingEntry(
		int rank,
		@JsonProperty("user_id") Long userId,
		String nickname,
		@JsonProperty("profile_image_url") String profileImageUrl,
		@JsonProperty("total_distance_km") BigDecimal totalDistanceKm,
		@JsonProperty("run_count") Long runCount,
		@JsonProperty("attendance_count") Long attendanceCount
) {}
