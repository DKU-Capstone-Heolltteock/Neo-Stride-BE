package com.neostride.server.community.service;

import com.neostride.server.community.api.BadgeProgressPort;
import com.neostride.server.platform.event.NotificationRequestedEvent;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CommunityBadgeService implements BadgeProgressPort {
	private final JdbcTemplate jdbcTemplate;
	private final ApplicationEventPublisher eventPublisher;

	public CommunityBadgeService(JdbcTemplate jdbcTemplate, ApplicationEventPublisher eventPublisher) {
		this.jdbcTemplate = jdbcTemplate;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public void improveBadgeIfHigher(long userId, String rawBadge) {
		String badge = normalizeBadge(rawBadge);
		if (userId <= 0 || badge == null || "NONE".equals(badge)) {
			return;
		}
		String previousBadge = currentBadge(userId);
		if (previousBadge == null || badgeRank(badge) <= badgeRank(previousBadge)) {
			return;
		}
		jdbcTemplate.update("""
			INSERT INTO community_users (user_id, community_profile_name, profile_photo, badge)
			SELECT user_id, COALESCE(community_profile_name, name), profile_photo, ?
			FROM users
			WHERE user_id = ? AND deleted_at IS NULL
			ON DUPLICATE KEY UPDATE badge = VALUES(badge)
			""", badge, userId);
		eventPublisher.publishEvent(new NotificationRequestedEvent(
				userId,
				"GRADE",
				badge + " 배지를 달성했습니다.",
				"/users/me/badge"
		));
	}

	private String currentBadge(long userId) {
		List<String> rows = jdbcTemplate.query("""
			SELECT COALESCE(cu.badge, 'NONE') AS badge
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id
			WHERE u.user_id = ? AND u.deleted_at IS NULL
			""", (rs, rowNum) -> rs.getString("badge"), userId);
		return rows == null || rows.isEmpty() || rows.getFirst() == null ? null : rows.getFirst();
	}

	private static String normalizeBadge(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
			case "BRONZE" -> "BRONZE";
			case "SILVER" -> "SILVER";
			case "GOLD" -> "GOLD";
			case "PLATINUM" -> "PLATINUM";
			case "DIAMOND" -> "DIAMOND";
			case "MASTER" -> "MASTER";
			case "CHALLENGER" -> "CHALLENGER";
			default -> "NONE";
		};
	}

	private static int badgeRank(String badge) {
		return switch (badge == null ? "NONE" : badge.trim().toUpperCase(java.util.Locale.ROOT)) {
			case "BRONZE" -> 1;
			case "SILVER" -> 2;
			case "GOLD" -> 3;
			case "PLATINUM" -> 4;
			case "DIAMOND" -> 5;
			case "MASTER" -> 6;
			case "CHALLENGER" -> 7;
			default -> 0;
		};
	}
}
