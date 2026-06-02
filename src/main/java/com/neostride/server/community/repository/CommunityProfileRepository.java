package com.neostride.server.community.repository;

import com.neostride.server.community.dto.AccountInfoResponse;
import com.neostride.server.community.dto.BadgeDetailResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

final class CommunityProfileRepository {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final JdbcTemplate jdbcTemplate;

	CommunityProfileRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	UserProfileResponse getUserProfile(long userId) {
		return getUserProfile(null, userId);
	}

	UserProfileResponse getUserProfile(Long viewerUserId, long userId) {
		List<UserProfileResponse> rows = jdbcTemplate.query("""
			SELECT COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_photo,
			       cu.status_message,
			       EXISTS (SELECT 1 FROM relationships r WHERE r.status = 'ACCEPTED'
			           AND ((r.user1_id = ? AND r.user2_id = u.user_id) OR (r.user2_id = ? AND r.user1_id = u.user_id))) AS is_friend,
			       EXISTS (SELECT 1 FROM relationships r WHERE r.status = 'BLOCKED' AND r.user1_id = ? AND r.user2_id = u.user_id) AS is_blocked,
			       EXISTS (SELECT 1 FROM relationships r WHERE r.status = 'REQUESTED' AND r.user1_id = ? AND r.user2_id = u.user_id) AS is_sent,
			       (SELECT COUNT(*) FROM relationships r WHERE (r.user1_id = u.user_id OR r.user2_id = u.user_id) AND r.status = 'ACCEPTED') AS friend_count,
			       (SELECT COUNT(*) FROM community_contents cc WHERE cc.author_user_id = u.user_id AND cc.content_type = 'POST') AS post_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.tagged_user_id = u.user_id AND ci.interaction_type = 'TAG') AS tagged_count,
			       (SELECT COUNT(DISTINCT ci.content_id) FROM community_interactions ci WHERE ci.user_id = u.user_id AND ci.interaction_type = 'COMMENT') AS commented_feed_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.user_id = u.user_id AND ci.interaction_type = 'LIKE') AS liked_feed_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.user_id = u.user_id AND ci.interaction_type = 'BOOKMARK') AS bookmarked_feed_count
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id WHERE u.user_id = ?
			""", (rs, n) -> new UserProfileResponse(rs.getString("nickname"), rs.getString("profile_photo"), rs.getString("status_message"), rs.getBoolean("is_friend"), rs.getBoolean("is_blocked"), rs.getBoolean("is_sent"), rs.getInt("friend_count"), rs.getInt("post_count"), rs.getInt("tagged_count"), rs.getInt("commented_feed_count"), rs.getInt("liked_feed_count"), rs.getInt("bookmarked_feed_count")), viewerUserId, viewerUserId, viewerUserId, viewerUserId, userId);
		return rows.isEmpty() ? new UserProfileResponse(null, null, null, false, false, false, 0, 0, 0, 0, 0, 0) : rows.getFirst();
	}

	AccountInfoResponse getAccountInfo(long userId) {
		List<AccountInfoResponse> rows = jdbcTemplate.query("""
			SELECT email, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_photo
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id WHERE u.user_id = ?
			""", (rs, n) -> new AccountInfoResponse(rs.getString("email"), rs.getString("nickname"), rs.getString("profile_photo")), userId);
		return rows.isEmpty() ? new AccountInfoResponse(null, null, null) : rows.getFirst();
	}

	void updateStatusMessage(long userId, String statusMessage) {
		jdbcTemplate.update("""
			INSERT INTO community_users (user_id, community_profile_name, profile_photo, status_message)
			SELECT user_id, COALESCE(community_profile_name, name), profile_photo, ? FROM users WHERE user_id = ?
			ON DUPLICATE KEY UPDATE status_message = VALUES(status_message)
			""", statusMessage, userId);
	}

	void updateNickname(long userId, String nickname) {
		jdbcTemplate.update("UPDATE users SET community_profile_name = ? WHERE user_id = ?", nickname, userId);
		jdbcTemplate.update("""
			INSERT INTO community_users (user_id, community_profile_name, profile_photo, status_message)
			SELECT user_id, COALESCE(?, community_profile_name, name), profile_photo, NULL FROM users WHERE user_id = ?
			ON DUPLICATE KEY UPDATE community_profile_name = VALUES(community_profile_name)
			""", nickname, userId);
	}

	boolean existsByCommunityProfileNameExcludingUserId(String nickname, long userId) {
		Integer count = jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*)
				FROM users u
				LEFT JOIN community_users cu ON cu.user_id = u.user_id
				WHERE (u.community_profile_name = ? OR cu.community_profile_name = ?) AND u.user_id <> ?
				""",
				Integer.class,
				nickname,
				nickname,
				userId
		);
		return count != null && count > 0;
	}

	void deleteAccount(long userId) {
		jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
	}

	void updateProfileImage(long userId, String profileImageUrl) {
		jdbcTemplate.update("""
			INSERT INTO community_users (user_id, community_profile_name, profile_photo, status_message)
			SELECT user_id, COALESCE(community_profile_name, name), ?, NULL FROM users WHERE user_id = ?
			ON DUPLICATE KEY UPDATE profile_photo = VALUES(profile_photo)
			""", profileImageUrl, userId);
		jdbcTemplate.update("UPDATE users SET profile_photo = ? WHERE user_id = ?", profileImageUrl, userId);
	}

	void deleteProfileImage(long userId) {
		jdbcTemplate.update("UPDATE community_users SET profile_photo = NULL WHERE user_id = ?", userId);
		jdbcTemplate.update("UPDATE users SET profile_photo = NULL WHERE user_id = ?", userId);
	}

	BadgeDetailResponse getBadgeDetail(long userId) {
		List<BadgeDetailResponse> rows = jdbcTemplate.query("""
			SELECT COALESCE(cu.badge, 'NONE') AS badge, rr.run_record_id, rr.total_distance, rr.pace, rr.created_at
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id
			LEFT JOIN running_records rr ON rr.user_id = u.user_id
			WHERE u.user_id = ? ORDER BY rr.total_distance DESC, rr.created_at DESC LIMIT 1
			""", (rs, n) -> new BadgeDetailResponse(rs.getString("badge"), nullableLong(rs.getObject("run_record_id")), nullToZero(rs.getBigDecimal("total_distance")), rs.getObject("pace") == null ? null : String.valueOf(rs.getInt("pace")), rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime().format(ISO)), userId);
		return rows.isEmpty() ? new BadgeDetailResponse("NONE", null, BigDecimal.ZERO, null, null) : rows.getFirst();
	}

	private static Long nullableLong(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}

	private static BigDecimal nullToZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}
}
