package com.neostride.server.community.dto;

import java.util.List;

public record FeedPageResponse(
		List<FeedUploadResponse> items,
		FeedCursorResponse nextCursor,
		boolean hasMore
) {}
