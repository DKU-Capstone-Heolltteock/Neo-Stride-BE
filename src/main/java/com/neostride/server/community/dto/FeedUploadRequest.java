package com.neostride.server.community.dto;

import java.math.BigDecimal;
import java.util.List;

public record FeedUploadRequest(
		String title,
		String content,
		String privacy,
		boolean mapVisible,
		String routeMapImageUri,
		List<Long> taggedUserIds,
		List<String> imageUrls,
		BigDecimal distance,
		String runningTime,
		String pace,
		int tagCount
) {}
