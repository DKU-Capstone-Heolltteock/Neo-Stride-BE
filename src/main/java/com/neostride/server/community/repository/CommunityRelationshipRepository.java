package com.neostride.server.community.repository;

import com.neostride.server.community.dto.FriendRequest;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.platform.event.NotificationRequestedEvent;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

final class CommunityRelationshipRepository {
	private final JdbcTemplate jdbcTemplate;
	private final ApplicationEventPublisher eventPublisher;

	CommunityRelationshipRepository(JdbcTemplate jdbcTemplate, ApplicationEventPublisher eventPublisher) {
		this.jdbcTemplate = jdbcTemplate;
		this.eventPublisher = eventPublisher;
	}

	List<FriendResponse> getFriendList(long userId, String status) {
		String normalized = status == null ? "friends" : status.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "sent" -> relationshipQuery(
					"r.user1_id = ? AND r.status = 'REQUESTED'",
					"sent",
					userId, userId
			);
			case "received" -> relationshipQuery(
					"r.user2_id = ? AND r.status = 'REQUESTED'",
					"received",
					userId, userId
			);
			case "blocked" -> relationshipQuery(
					"r.user1_id = ? AND r.status = 'BLOCKED'",
					"blocked",
					userId, userId
			);
			default -> relationshipQuery(
					"(r.user1_id = ? OR r.user2_id = ?) AND r.status = 'ACCEPTED'",
					"friends",
					userId, userId, userId
			);
		};
	}

	List<FriendResponse> getUserFriendList(long viewerUserId, long userId) {
		return jdbcTemplate.query("""
			SELECT other_user.user_id, COALESCE(cu.community_profile_name, other_user.community_profile_name, other_user.name) AS nickname,
			       COALESCE(cu.badge, 'NONE') AS badge_tier,
			       (SELECT COUNT(*) FROM relationships rf WHERE (rf.user1_id = other_user.user_id OR rf.user2_id = other_user.user_id) AND rf.status='ACCEPTED') AS friend_count,
			       COALESCE(cu.profile_photo, other_user.profile_photo) AS profile_image_url,
			       CASE
			           WHEN EXISTS (SELECT 1 FROM relationships rv WHERE rv.status='BLOCKED' AND rv.user1_id=? AND rv.user2_id=other_user.user_id) THEN 'blocked'
			           WHEN EXISTS (SELECT 1 FROM relationships rv WHERE rv.status='ACCEPTED' AND ((rv.user1_id=? AND rv.user2_id=other_user.user_id) OR (rv.user2_id=? AND rv.user1_id=other_user.user_id))) THEN 'friends'
			           WHEN EXISTS (SELECT 1 FROM relationships rv WHERE rv.status='REQUESTED' AND rv.user1_id=? AND rv.user2_id=other_user.user_id) THEN 'sent'
			           WHEN EXISTS (SELECT 1 FROM relationships rv WHERE rv.status='REQUESTED' AND rv.user2_id=? AND rv.user1_id=other_user.user_id) THEN 'received'
			           ELSE 'none'
			       END AS relationship_status
			FROM relationships r
			JOIN users other_user ON other_user.user_id = CASE WHEN r.user1_id = ? THEN r.user2_id ELSE r.user1_id END
			LEFT JOIN community_users cu ON cu.user_id = other_user.user_id
			WHERE (r.user1_id = ? OR r.user2_id = ?) AND r.status = 'ACCEPTED'
			ORDER BY other_user.user_id
			""", (rs, n) -> new FriendResponse(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("badge_tier"), rs.getInt("friend_count"), rs.getString("profile_image_url"), rs.getString("relationship_status")),
				viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId, userId, userId, userId);
	}

	void updateRelationship(long userId, FriendRequest request) {
		if (request == null) throw new IllegalArgumentException("요청 본문이 필요합니다.");
		if (request.targetId() == null || request.targetId() <= 0 || request.targetId() == userId) throw new IllegalArgumentException("target_id가 올바르지 않습니다.");
		switch ((request.action() == null ? "" : request.action().trim()).toLowerCase(Locale.ROOT)) {
			case "request" -> {
				jdbcTemplate.update("INSERT INTO relationships (user1_id, user2_id, status) VALUES (?, ?, 'REQUESTED') ON DUPLICATE KEY UPDATE status = 'REQUESTED'", userId, request.targetId());
				notifyFriendRequest(userId, request.targetId());
			}
			case "accept" -> jdbcTemplate.update("UPDATE relationships SET status='ACCEPTED' WHERE user1_id=? AND user2_id=?", request.targetId(), userId);
			case "reject" -> jdbcTemplate.update("UPDATE relationships SET status='REJECTED' WHERE user1_id=? AND user2_id=?", request.targetId(), userId);
			case "block" -> {
				jdbcTemplate.update("DELETE FROM relationships WHERE (user1_id=? AND user2_id=?) OR (user1_id=? AND user2_id=?)", userId, request.targetId(), request.targetId(), userId);
				jdbcTemplate.update("INSERT INTO relationships (user1_id, user2_id, status) VALUES (?, ?, 'BLOCKED')", userId, request.targetId());
				jdbcTemplate.update("""
					DELETE ci FROM community_interactions ci
					JOIN community_contents cc ON cc.content_id = ci.content_id
					WHERE ci.user_id = ? AND cc.author_user_id = ?
					""", userId, request.targetId());
			}
			case "cancel" -> jdbcTemplate.update("DELETE FROM relationships WHERE user1_id=? AND user2_id=? AND status='REQUESTED'", userId, request.targetId());
			case "unblock" -> jdbcTemplate.update("DELETE FROM relationships WHERE user1_id=? AND user2_id=? AND status='BLOCKED'", userId, request.targetId());
			case "delete", "unfriend" -> jdbcTemplate.update("DELETE FROM relationships WHERE status <> 'BLOCKED' AND ((user1_id=? AND user2_id=?) OR (user1_id=? AND user2_id=?))", userId, request.targetId(), request.targetId(), userId);
			default -> throw new IllegalArgumentException("지원하지 않는 relationship action입니다.");
		}
	}

	private List<FriendResponse> relationshipQuery(String predicate, String responseStatus, Object... args) {
		return jdbcTemplate.query("""
			SELECT other_user.user_id, COALESCE(cu.community_profile_name, other_user.community_profile_name, other_user.name) AS nickname,
			       COALESCE(cu.badge, 'NONE') AS badge_tier,
			       (SELECT COUNT(*) FROM relationships rf WHERE (rf.user1_id = other_user.user_id OR rf.user2_id = other_user.user_id) AND rf.status='ACCEPTED') AS friend_count,
			       COALESCE(cu.profile_photo, other_user.profile_photo) AS profile_image_url
			FROM relationships r
			JOIN users other_user ON other_user.user_id = CASE WHEN r.user1_id = ? THEN r.user2_id ELSE r.user1_id END
			LEFT JOIN community_users cu ON cu.user_id = other_user.user_id
			WHERE
			""" + predicate + " ORDER BY other_user.user_id", (rs, n) -> new FriendResponse(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("badge_tier"), rs.getInt("friend_count"), rs.getString("profile_image_url"), responseStatus), args);
	}

	private void notifyFriendRequest(long requesterUserId, long targetUserId) {
		if (requesterUserId == targetUserId) return;
		eventPublisher.publishEvent(new NotificationRequestedEvent(targetUserId, "FRIEND_REQUEST", notificationActorName(requesterUserId) + "님이 친구 요청을 보냈습니다.", "/community/friends"));
	}

	private String notificationActorName(long userId) {
		List<String> rows = jdbcTemplate.query("""
			SELECT COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id
			WHERE u.user_id = ?
			""", (rs, n) -> rs.getString("nickname"), userId);
		return rows == null || rows.isEmpty() || rows.getFirst() == null || rows.getFirst().isBlank() ? "러너" : rows.getFirst();
	}
}
