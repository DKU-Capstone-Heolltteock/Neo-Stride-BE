package com.neostride.server.community.dto;

import java.util.List;

public record MyCommentActivityPageResponse(
		List<MyCommentActivityResponse> items,
		CommentCursorResponse nextCursor,
		boolean hasMore
) {}
