package com.neostride.server.community.dto;

public record CommentResponse(
		Long commentId,
		Long writerId,
		String nickname,
		String profileImageUrl,
		String content,
		String createdAt,
		boolean badgeOwned,
		String badgeType,
		boolean mine
) {}
