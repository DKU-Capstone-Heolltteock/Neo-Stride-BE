package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
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
	@JsonCreator
	public static FeedUploadRequest fromJson(
			@JsonProperty("title") String title,
			@JsonProperty("content") String content,
			@JsonProperty("privacy") String privacy,
			@JsonProperty("mapVisible") @JsonAlias("map_visible") Boolean mapVisible,
			@JsonProperty("routeMapImageUri") @JsonAlias({"route_map_image_uri", "route_map_image"}) String routeMapImageUri,
			@JsonProperty("taggedUserIds") @JsonAlias("tagged_user_ids") List<Long> taggedUserIds,
			@JsonProperty("imageUrls") @JsonAlias("image_urls") List<String> imageUrls,
			@JsonProperty("distance") @JsonAlias({"total_distance", "totalDistance"}) BigDecimal distance,
			@JsonProperty("runningTime") @JsonAlias({"running_time", "duration"}) String runningTime,
			@JsonProperty("pace") @JsonAlias({"running_pace", "runningPace"}) String pace,
			@JsonProperty("tagCount") @JsonAlias("tag_count") Integer tagCount,
			@JsonProperty("running_record_id") @JsonAlias({"run_record_id", "runningRecordId", "runRecordId"}) Long runningRecordId
	) {
		return new FeedUploadRequest(title, content, privacy, Boolean.TRUE.equals(mapVisible), routeMapImageUri, taggedUserIds, imageUrls, distance, runningTime, pace, tagCount == null ? 0 : tagCount, runningRecordId);
	}

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
