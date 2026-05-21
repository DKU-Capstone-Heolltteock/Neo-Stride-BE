package com.neostride.server.community.dto;

import java.util.List;

public record TipDetailResponse(
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
		String courseAddress,
		List<String> imageUrls,
		int likeCount,
		int commentCount,
		boolean liked,
		boolean bookmarked,
		boolean mine,
		String createdAt,
		List<CommentResponse> comments
) {}
