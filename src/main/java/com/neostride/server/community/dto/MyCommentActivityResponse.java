package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record MyCommentActivityResponse(
		@JsonProperty("content_type") String contentType,
		@JsonProperty("content_id") Long contentId,
		@JsonProperty("writer_id") Long writerId,
		String nickname,
		@JsonProperty("profile_image_url") String profileImageUrl,
		@JsonProperty("badge_owned") boolean badgeOwned,
		@JsonProperty("badge_type") String badgeType,
		String category,
		@JsonProperty("content_title") String contentTitle,
		@JsonProperty("content_text") String contentText,
		@JsonProperty("content_created_at") String contentCreatedAt,
		@JsonProperty("total_distance") BigDecimal totalDistance,
		Integer duration,
		Integer pace,
		@JsonProperty("gps_visible") boolean gpsVisible,
		@JsonProperty("route_map_url") String routeMapUrl,
		@JsonProperty("image_urls") List<String> imageUrls,
		@JsonProperty("like_count") int likeCount,
		@JsonProperty("comment_count") int commentCount,
		@JsonProperty("tag_count") int tagCount,
		@JsonProperty("is_liked") boolean liked,
		@JsonProperty("is_bookmarked") boolean bookmarked,
		@JsonProperty("is_commented") boolean commented,
		@JsonProperty("is_tagged") boolean tagged,
		@JsonProperty("content_mine") boolean contentMine,
		@JsonProperty("comment_id") Long commentId,
		@JsonProperty("comment_text") String commentText,
		@JsonProperty("comment_created_at") String commentCreatedAt,
		@JsonProperty("comment_mine") boolean commentMine
) {}
