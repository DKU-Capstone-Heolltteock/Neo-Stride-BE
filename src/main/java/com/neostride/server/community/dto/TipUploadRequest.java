package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TipUploadRequest(
		String category,
		String title,
		String content,
		@JsonAlias({"gpsVisible", "gps_visible"})
		boolean gpsVisible,
		@JsonAlias({"routeMapImageUrl", "route_map_image_url", "route_map_image", "routeMapImage"})
		String routeMapImageUrl,
		@JsonAlias({"courseAddress", "course_address"})
		String courseAddress,
		@JsonAlias({"imageUrls", "image_urls"})
		List<String> imageUrls
) {
	@JsonCreator
	public static TipUploadRequest fromJson(
			@JsonProperty("category") String category,
			@JsonProperty("title") String title,
			@JsonProperty("content") String content,
			@JsonProperty("gpsVisible") @JsonAlias("gps_visible") Boolean gpsVisible,
			@JsonProperty("routeMapImageUrl") @JsonAlias({"route_map_image_url", "route_map_image", "routeMapImage"}) String routeMapImageUrl,
			@JsonProperty("courseAddress") @JsonAlias("course_address") String courseAddress,
			@JsonProperty("imageUrls") @JsonAlias("image_urls") List<String> imageUrls
	) {
		return new TipUploadRequest(category, title, content, Boolean.TRUE.equals(gpsVisible), routeMapImageUrl, courseAddress, imageUrls);
	}

	public TipUploadRequest(String category, String title, String content, boolean gpsVisible, String routeMapImageUrl, List<String> imageUrls) {
		this(category, title, content, gpsVisible, routeMapImageUrl, null, imageUrls);
	}
}
