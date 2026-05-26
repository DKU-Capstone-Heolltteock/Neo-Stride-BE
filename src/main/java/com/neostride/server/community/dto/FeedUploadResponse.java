package com.neostride.server.community.dto;

import java.util.List;

public record FeedUploadResponse(
		Long feedId,
		String profileImageUrl,
		String nickname,
		boolean badgeOwned,
		String badgeType,
		String createdAt,
		String title,
		String content,
		int taggedCount,
		int likeCount,
		int commentCount,
		String distance,
		String duration,
		String pace,
		boolean mapVisible,
		String routeMapImageUri,
		List<String> imageUrls,
		boolean liked,
		boolean bookmarked,
		boolean commented,
		boolean tagged,
		boolean mine,
		Long writerId
) {
	public FeedUploadResponse(Long feedId, String profileImageUrl, String nickname, String createdAt, String title,
						  String content, int taggedCount, int likeCount, int commentCount, String distance,
						  String duration, String pace, boolean mapVisible, String routeMapImageUri,
						  List<String> imageUrls) {
		this(feedId, profileImageUrl, nickname, false, "NONE", createdAt, title, content, taggedCount, likeCount, commentCount,
				distance, duration, pace, mapVisible, routeMapImageUri, imageUrls, false, false, false, false, false, null);
	}

	public FeedUploadResponse(Long feedId, String profileImageUrl, String nickname, String createdAt, String title,
						  String content, int taggedCount, int likeCount, int commentCount, String distance,
						  String duration, String pace, boolean mapVisible, String routeMapImageUri,
						  List<String> imageUrls, boolean mine, Long writerId) {
		this(feedId, profileImageUrl, nickname, false, "NONE", createdAt, title, content, taggedCount, likeCount, commentCount,
				distance, duration, pace, mapVisible, routeMapImageUri, imageUrls, false, false, false, false, mine, writerId);
	}
}
