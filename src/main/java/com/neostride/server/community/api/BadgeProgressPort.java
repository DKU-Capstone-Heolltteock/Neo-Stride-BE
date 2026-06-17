package com.neostride.server.community.api;

public interface BadgeProgressPort {
	void improveBadgeIfHigher(long userId, String rawBadge);
}
