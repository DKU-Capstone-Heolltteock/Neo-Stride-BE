package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record CommunityContentResponse(
		@JsonProperty("content_id") Long contentId,
		@JsonProperty("user_id") Long userId,
		String nickname,
		@JsonProperty("content_title") String contentTitle,
		@JsonProperty("content_text") String contentText,
		@JsonProperty("total_distance") BigDecimal totalDistance,
		Integer duration,
		Integer pace,
		@JsonProperty("created_at") String createdAt,
		@JsonProperty("profile_image_url") String profileImageUrl,
		@JsonProperty("image_urls") List<String> imageUrls,
		@JsonProperty("like_count") int likeCount,
		@JsonProperty("comment_count") int commentCount,
		@JsonProperty("tag_count") int tagCount,
		@JsonProperty("is_liked") boolean liked,
		@JsonProperty("is_bookmarked") boolean bookmarked,
		@JsonProperty("is_commented") boolean commented,
		@JsonProperty("is_tagged") boolean tagged,
		@JsonProperty("badge_tier") String badgeTier,
		@JsonProperty("route_map_url") String routeMapUrl
) {
	public CommunityContentResponse(Long contentId, String contentText, BigDecimal totalDistance, Integer duration, Integer pace, String createdAt) {
		this(contentId, null, null, null, contentText, totalDistance, duration, pace, createdAt, null, List.of(), 0, 0, 0, false, false, false, false, "NONE", null);
	}

	public CommunityContentResponse(Long contentId, String contentTitle, String contentText, BigDecimal totalDistance,
								Integer duration, Integer pace, String createdAt) {
		this(contentId, null, null, contentTitle, contentText, totalDistance, duration, pace, createdAt, null, List.of(), 0, 0, 0, false, false, false, false, "NONE", null);
	}

	public CommunityContentResponse(Long contentId, String contentTitle, String contentText, BigDecimal totalDistance,
								Integer duration, Integer pace, String createdAt, String profileImageUrl, List<String> imageUrls) {
		this(contentId, null, null, contentTitle, contentText, totalDistance, duration, pace, createdAt, profileImageUrl, imageUrls, 0, 0, 0, false, false, false, false, "NONE", null);
	}
}
