package com.neostride.server.community.dto;

import java.util.List;

public record TipUploadResponse(
		Long tipId,
		Long writerId,
		String nickname,
		String profileImageUrl,
		boolean badgeOwned,
		String badgeType,
		String category,
		String title,
		String content,
		boolean gpsVisible,
		String routeMapImageUrl,
		List<String> imageUrls,
		int likeCount,
		int commentCount,
		boolean liked,
		boolean bookmarked,
		boolean commented,
		boolean mine,
		String createdAt
) {
	public TipUploadResponse(Long tipId, String nickname, String profileImageUrl, boolean badgeOwned, String category,
						 String title, String content, boolean gpsVisible, String routeMapImageUrl,
						 List<String> imageUrls, int likeCount, int commentCount, String createdAt) {
		this(tipId, null, nickname, profileImageUrl, badgeOwned, badgeOwned ? "UNKNOWN" : "NONE", category, title,
				content, gpsVisible, routeMapImageUrl, imageUrls, likeCount, commentCount, false, false, false, false, createdAt);
	}
}
