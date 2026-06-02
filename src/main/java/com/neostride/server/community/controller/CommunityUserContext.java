package com.neostride.server.community.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import org.springframework.stereotype.Component;

@Component
public class CommunityUserContext {
	private final AuthenticatedUserService authenticatedUserService;

	public CommunityUserContext(AuthenticatedUserService authenticatedUserService) {
		this.authenticatedUserService = authenticatedUserService;
	}

	public long authenticatedUserId(String authorization, Long headerUserId) {
		long authenticatedUserId = authenticatedUserService.requireUserId(authorization);
		authenticatedUserService.requireSameUserIfPresent(authenticatedUserId, headerUserId, "X-User-Id");
		return authenticatedUserId;
	}

	public Long optionalUserId(String authorization, Long headerUserId) {
		if (authorization != null && !authorization.isBlank()) {
			return authenticatedUserId(authorization, headerUserId);
		}
		return null;
	}
}
