package com.neostride.server.community.dto;

import java.util.List;

public record TipListResponse(
		List<TipUploadResponse> tips
) {}
