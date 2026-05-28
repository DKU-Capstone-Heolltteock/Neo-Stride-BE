package com.neostride.server.community.dto;

import java.util.List;

public record CommentPageResponse(
		List<CommentResponse> items,
		CommentCursorResponse nextCursor,
		boolean hasMore
) {}
