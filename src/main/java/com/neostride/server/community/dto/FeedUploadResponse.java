package com.neostride.server.community.dto;

import java.util.List;

public record FeedUploadResponse(
		Long feedId,
		String profileImageUrl,
		String nickname,
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
		List<String> imageUrls
) {}
