package com.neostride.server.community.dto;

import java.util.List;

public record TipUploadRequest(
		String category,
		String title,
		String content,
		boolean gpsVisible,
		String routeMapImageUrl,
		List<String> imageUrls
) {}
