package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record FeedUploadRequest(
		String title,
		String content,
		String privacy,
		@JsonAlias({"mapVisible", "map_visible"})
		boolean mapVisible,
		@JsonAlias({"routeMapImageUri", "route_map_image_uri", "route_map_image"})
		String routeMapImageUri,
		List<Long> taggedUserIds,
		List<String> imageUrls,
		@JsonAlias({"distance", "total_distance", "totalDistance"})
		BigDecimal distance,
		@JsonAlias({"runningTime", "running_time", "duration"})
		String runningTime,
		@JsonAlias({"pace", "running_pace", "runningPace"})
		String pace,
		int tagCount,
		@JsonProperty("running_record_id")
		@JsonAlias({"run_record_id", "runningRecordId", "runRecordId"})
		Long runningRecordId
) {
	public FeedUploadRequest(
			String title,
			String content,
			String privacy,
			boolean mapVisible,
			String routeMapImageUri,
			List<Long> taggedUserIds,
			List<String> imageUrls,
			BigDecimal distance,
			String runningTime,
			String pace,
			int tagCount
	) {
		this(title, content, privacy, mapVisible, routeMapImageUri, taggedUserIds, imageUrls, distance, runningTime, pace, tagCount, null);
	}
}
