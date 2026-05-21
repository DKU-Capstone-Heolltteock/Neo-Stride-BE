package com.neostride.server.community.dto;

import java.util.List;

public record FeedDetailResponse(
		Long feedId,
		Long writerId,
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
		boolean liked,
		boolean bookmarked,
		boolean mine,
		String distance,
		String duration,
		String pace,
		boolean mapVisible,
		String routeMapImageUri,
		List<String> imageUrls,
		List<CommentResponse> comments
) {}
