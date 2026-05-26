package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record CommunityContentResponse(
		@JsonProperty("content_id") Long contentId,
		@JsonProperty("content_title") String contentTitle,
		@JsonProperty("content_text") String contentText,
		@JsonProperty("total_distance") BigDecimal totalDistance,
		Integer duration,
		Integer pace,
		@JsonProperty("created_at") String createdAt,
		@JsonProperty("profile_image_url") String profileImageUrl,
		@JsonProperty("image_urls") List<String> imageUrls
) {
	public CommunityContentResponse(Long contentId, String contentText, BigDecimal totalDistance, Integer duration, Integer pace, String createdAt) {
		this(contentId, null, contentText, totalDistance, duration, pace, createdAt, null, List.of());
	}

	public CommunityContentResponse(Long contentId, String contentTitle, String contentText, BigDecimal totalDistance,
								Integer duration, Integer pace, String createdAt) {
		this(contentId, contentTitle, contentText, totalDistance, duration, pace, createdAt, null, List.of());
	}
}
