package com.neostride.server.community.dto;

import java.util.List;

public record TipUploadRequest(
		String category,
		String title,
		String content,
		boolean gpsVisible,
		String routeMapImageUrl,
		String courseAddress,
		List<String> imageUrls
) {
	public TipUploadRequest(String category, String title, String content, boolean gpsVisible, String routeMapImageUrl, List<String> imageUrls) {
		this(category, title, content, gpsVisible, routeMapImageUrl, null, imageUrls);
	}
}
