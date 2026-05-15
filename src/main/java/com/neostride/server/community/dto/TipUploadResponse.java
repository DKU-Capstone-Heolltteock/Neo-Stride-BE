package com.neostride.server.community.dto;

import java.util.List;

public record TipUploadResponse(
		Long tipId,
		String nickname,
		String profileImageUrl,
		boolean badgeOwned,
		String category,
		String title,
		String content,
		boolean gpsVisible,
		String routeMapImageUrl,
		List<String> imageUrls,
		int likeCount,
		int commentCount,
		String createdAt
) {}
