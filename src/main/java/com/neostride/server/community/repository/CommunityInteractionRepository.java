package com.neostride.server.community.repository;

import com.neostride.server.community.dto.CommentRequest;
import com.neostride.server.community.dto.CommentResponse;
import com.neostride.server.notification.repository.NotificationRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

final class CommunityInteractionRepository {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
	private static final String VIEWER_SCOPE_PREDICATE = "(cc.feed_scope = 'PUBLIC' OR cc.author_user_id = ? OR (cc.feed_scope = 'FRIENDS' AND EXISTS (SELECT 1 FROM relationships r WHERE r.status = 'ACCEPTED' AND ((r.user1_id = ? AND r.user2_id = cc.author_user_id) OR (r.user2_id = ? AND r.user1_id = cc.author_user_id)))))";

	private final JdbcTemplate jdbcTemplate;
	private final NotificationRepository notificationRepository;

	CommunityInteractionRepository(JdbcTemplate jdbcTemplate, NotificationRepository notificationRepository) {
		this.jdbcTemplate = jdbcTemplate;
		this.notificationRepository = notificationRepository;
	}

	boolean toggleInteraction(long userId, long contentId, String type) {
		requireViewableContent(userId, contentId);
		String normalizedType = normalizeInteractionType(type);
		List<Long> existing = jdbcTemplate.query(
				"SELECT interaction_id FROM community_interactions WHERE user_id=? AND content_id=? AND interaction_type=? ORDER BY interaction_id ASC LIMIT 1",
				(rs, n) -> rs.getLong("interaction_id"), userId, contentId, normalizedType);
		if (!existing.isEmpty()) {
			jdbcTemplate.update("DELETE FROM community_interactions WHERE interaction_id=?", existing.getFirst());
			return false;
		}
		try {
			jdbcTemplate.update("INSERT INTO community_interactions (user_id, content_id, interaction_type) VALUES (?, ?, ?)", userId, contentId, normalizedType);
		} catch (DuplicateKeyException duplicate) {
			return true;
		}
		if ("LIKE".equals(normalizedType)) {
			notifyContentInteraction(userId, contentId, "LIKE");
		}
		return true;
	}

	CommentResponse createComment(long userId, long contentId, CommentRequest request) {
		requireViewableContent(userId, contentId);
		KeyHolder kh = new GeneratedKeyHolder();
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement("INSERT INTO community_interactions (user_id, content_id, interaction_type, comment_text) VALUES (?, ?, 'COMMENT', ?)", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, userId);
			ps.setLong(2, contentId);
			ps.setString(3, request.content());
			return ps;
		}, kh);
		long commentId = generatedKey(kh, "댓글 ID를 생성하지 못했습니다.");
		notifyContentInteraction(userId, contentId, "COMMENT");
		return findComment(userId, commentId);
	}

	CommentResponse updateComment(long userId, long contentId, long commentId, CommentRequest request) {
		jdbcTemplate.update("UPDATE community_interactions SET comment_text=? WHERE interaction_id=? AND user_id=? AND content_id=? AND interaction_type='COMMENT'", request.content(), commentId, userId, contentId);
		return findComment(userId, commentId);
	}

	void deleteComment(long userId, long contentId, long commentId) {
		jdbcTemplate.update("DELETE FROM community_interactions WHERE interaction_id=? AND user_id=? AND content_id=? AND interaction_type='COMMENT'", commentId, userId, contentId);
	}

	List<CommentResponse> commentsPage(long viewerUserId, long contentId, LocalDateTime cursorCreatedAt, Long cursorId, int limit) {
		requireViewableContent(viewerUserId, contentId);
		return commentsForContent(viewerUserId, contentId, cursorCreatedAt, cursorId, limit);
	}

	List<CommentResponse> commentsForContent(long viewerUserId, long contentId, LocalDateTime cursorCreatedAt, Long cursorId, Integer limit) {
		String cursorPredicate = "";
		java.util.List<Object> args = new java.util.ArrayList<>();
		args.add(contentId);
		args.add(viewerUserId);
		if (cursorCreatedAt != null && cursorId != null) {
			cursorPredicate = " AND (ci.created_at > ? OR (ci.created_at = ? AND ci.interaction_id > ?))";
			Timestamp cursorTimestamp = Timestamp.valueOf(cursorCreatedAt);
			args.add(cursorTimestamp);
			args.add(cursorTimestamp);
			args.add(cursorId);
		}
		if (limit != null) {
			args.add(limit);
		}
		return jdbcTemplate.query("""
			SELECT ci.interaction_id, ci.user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, ci.comment_text, ci.created_at,
			       COALESCE(cu.badge, 'NONE') AS badge
			FROM community_interactions ci JOIN users u ON u.user_id=ci.user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE ci.content_id=? AND ci.interaction_type='COMMENT'
			  AND NOT EXISTS (
			  SELECT 1 FROM relationships r
			  WHERE r.user1_id=? AND r.user2_id=ci.user_id AND r.status='BLOCKED'
			  )
			""" + cursorPredicate + """
			ORDER BY ci.created_at ASC, ci.interaction_id ASC
			""" + (limit == null ? "" : " LIMIT ?"), (rs, n) -> mapComment(rs, viewerUserId), args.toArray());
	}

	private CommentResponse findComment(long viewerUserId, long commentId) {
		return jdbcTemplate.query("""
			SELECT ci.interaction_id, ci.user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, ci.comment_text, ci.created_at,
			       COALESCE(cu.badge, 'NONE') AS badge
			FROM community_interactions ci JOIN users u ON u.user_id=ci.user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE ci.interaction_id=? AND ci.interaction_type='COMMENT'
			""", (rs, n) -> mapComment(rs, viewerUserId), commentId).stream().findFirst().orElse(null);
	}

	private CommentResponse mapComment(java.sql.ResultSet rs, long viewerUserId) throws java.sql.SQLException {
		String badge = rs.getString("badge");
		long writerId = rs.getLong("user_id");
		return new CommentResponse(rs.getLong("interaction_id"), writerId, rs.getString("nickname"), rs.getString("profile_image_url"), rs.getString("comment_text"), rs.getTimestamp("created_at").toLocalDateTime().format(ISO), badgeOwned(badge), badge, writerId == viewerUserId);
	}

	private void requireViewableContent(long viewerUserId, long contentId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM community_contents cc WHERE cc.content_id=? AND (cc.content_type='TIP' OR (cc.content_type='POST' AND " + VIEWER_SCOPE_PREDICATE + ")) AND " + blockedByCurrentUserPredicate(),
				Integer.class, contentId, viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId);
		if (count == null || count == 0) {
			throw new IllegalArgumentException("접근할 수 없는 콘텐츠입니다.");
		}
	}

	private void notifyContentInteraction(long actorUserId, long contentId, String interactionType) {
		ContentOwner owner = contentOwner(contentId);
		if (owner == null || owner.authorUserId() == actorUserId) return;
		String actorName = notificationActorName(actorUserId);
		boolean tip = "TIP".equalsIgnoreCase(owner.contentType());
		String endpoint = tip ? "/api/community/tips/" + contentId : "/api/community/feeds/" + contentId;
		if ("LIKE".equalsIgnoreCase(interactionType)) {
			notificationRepository.createNotification(owner.authorUserId(), tip ? "TIP_LIKE" : "FEED_LIKE", actorName + "님이 회원님의 " + (tip ? "팁" : "피드") + "을 좋아합니다.", endpoint);
		}
		if ("COMMENT".equalsIgnoreCase(interactionType)) {
			notificationRepository.createNotification(owner.authorUserId(), tip ? "TIP_COMMENT" : "FEED_COMMENT", actorName + "님이 회원님의 " + (tip ? "팁" : "피드") + "에 댓글을 남겼습니다.", endpoint);
		}
	}

	private ContentOwner contentOwner(long contentId) {
		List<ContentOwner> rows = jdbcTemplate.query("""
			SELECT author_user_id, content_type
			FROM community_contents
			WHERE content_id = ?
			""", (rs, n) -> new ContentOwner(rs.getLong("author_user_id"), rs.getString("content_type")), contentId);
		return rows == null || rows.isEmpty() ? null : rows.getFirst();
	}

	private String notificationActorName(long userId) {
		List<String> rows = jdbcTemplate.query("""
			SELECT COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id
			WHERE u.user_id = ?
			""", (rs, n) -> rs.getString("nickname"), userId);
		return rows == null || rows.isEmpty() || rows.getFirst() == null || rows.getFirst().isBlank() ? "러너" : rows.getFirst();
	}

	private static String normalizeInteractionType(String type) {
		String normalized = type == null ? null : type.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "LIKE", "BOOKMARK" -> normalized;
			default -> throw new IllegalArgumentException("지원하지 않는 상호작용 타입입니다.");
		};
	}

	private static long generatedKey(KeyHolder keyHolder, String message) {
		Object key = keyHolder.getKey();
		if (key == null && keyHolder.getKeys() != null && keyHolder.getKeys().containsKey("GENERATED_KEY")) {
			key = keyHolder.getKeys().get("GENERATED_KEY");
		}
		if (key == null && keyHolder.getKeys() != null && keyHolder.getKeys().containsKey("content_id")) {
			key = keyHolder.getKeys().get("content_id");
		}
		if (key == null || !(key instanceof Number number)) {
			throw new IllegalStateException(message);
		}
		return number.longValue();
	}

	private static boolean badgeOwned(String badge) {
		return badge != null && !"NONE".equalsIgnoreCase(badge);
	}

	private static String blockedByCurrentUserPredicate() {
		return "(\n" +
			"	NOT EXISTS (\n" +
			"		SELECT 1 FROM relationships r\n" +
			"		WHERE r.status = 'BLOCKED'\n" +
			"		  AND ((r.user1_id = ? AND r.user2_id = cc.author_user_id)\n" +
			"		   OR (r.user2_id = ? AND r.user1_id = cc.author_user_id))\n" +
		")" +
		")";
	}

	private record ContentOwner(long authorUserId, String contentType) {}
}
