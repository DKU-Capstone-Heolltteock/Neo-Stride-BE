package com.neostride.server.community.repository;

import com.neostride.server.community.dto.TipDetailResponse;
import com.neostride.server.community.dto.TipUploadRequest;
import com.neostride.server.community.dto.TipUploadResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

final class CommunityTipRepository {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final JdbcTemplate jdbcTemplate;
	private final CommunityInteractionRepository interactionRepository;

	CommunityTipRepository(JdbcTemplate jdbcTemplate, CommunityInteractionRepository interactionRepository) {
		this.jdbcTemplate = jdbcTemplate;
		this.interactionRepository = interactionRepository;
	}

	long insertTip(long userId, TipUploadRequest request) {
		KeyHolder kh = new GeneratedKeyHolder();
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement("""
				INSERT INTO community_contents (author_user_id, include_route_detail, content_type, tip_type, feed_scope, content_text, image, title, body_text, route_map_image_url, course_address)
				VALUES (?, ?, 'TIP', ?, 'PUBLIC', ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, userId);
			ps.setBoolean(2, request != null && request.gpsVisible());
			ps.setString(3, normalizeTipType(request == null ? null : request.category()));
			ps.setString(4, CommunityContentCodec.encodeTipContent(request == null ? null : request.title(), request == null ? null : request.content(), request == null ? null : request.routeMapImageUrl(), request == null ? null : request.courseAddress()));
			ps.setString(5, CommunityContentCodec.encodeImages(request == null ? null : request.imageUrls()));
			ps.setString(6, request == null ? null : request.title());
			ps.setString(7, request == null ? null : request.content());
			ps.setString(8, request == null ? null : request.routeMapImageUrl());
			ps.setString(9, request == null ? null : request.courseAddress());
			return ps;
		}, kh);
		long contentId = generatedKey(kh, "팁 ID를 생성하지 못했습니다.");
		insertContentImages(contentId, request == null ? null : request.imageUrls());
		return contentId;
	}

	TipUploadResponse findTip(long tipId) {
		return tipQuery("cc.content_id = ?", tipId).stream().findFirst().orElse(null);
	}

	List<TipUploadResponse> listTips() {
		return tipQuery("cc.content_type = 'TIP'");
	}

	List<TipUploadResponse> listTips(long viewerUserId) {
		String predicate = "cc.content_type = 'TIP'";
		if (viewerUserId <= 0) return listTips();
		return tipQueryForViewer(predicate + " AND " + blockedByCurrentUserPredicate(), viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId);
	}

	List<TipUploadResponse> listTipsByUser(long userId) {
		return listTipsByUser(null, userId);
	}

	List<TipUploadResponse> listTipsByUser(Long viewerUserId, long userId) {
		long effectiveViewerUserId = viewerUserId == null ? userId : viewerUserId;
		return tipQueryForViewer("cc.content_type = 'TIP' AND cc.author_user_id = ? AND " + blockedByCurrentUserPredicate(), effectiveViewerUserId, effectiveViewerUserId, effectiveViewerUserId, effectiveViewerUserId, userId, effectiveViewerUserId, effectiveViewerUserId);
	}

	List<TipUploadResponse> listTipsLikedByUser(long userId) {
		return listTipsInteractedByType(userId, "LIKE");
	}

	List<TipUploadResponse> listTipsBookmarkedByUser(long userId) {
		return listTipsInteractedByType(userId, "BOOKMARK");
	}

	List<TipUploadResponse> listTipsCommentedByUser(long userId) {
		return listTipsInteractedByType(userId, "COMMENT");
	}

	TipDetailResponse findTipDetail(long userId, long tipId, Integer commentLimit) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, CASE WHEN u.deleted_at IS NULL THEN COALESCE(cu.community_profile_name, u.community_profile_name, u.name) ELSE '탈퇴한 사용자' END AS nickname,
			       CASE WHEN u.deleted_at IS NULL THEN COALESCE(cu.profile_photo, u.profile_photo) ELSE NULL END AS profile_image_url, CASE WHEN u.deleted_at IS NULL THEN COALESCE(cu.badge, 'NONE') ELSE 'NONE' END AS badge,
			       cc.tip_type, cc.content_text, cc.title, cc.body_text, cc.route_map_image_url, cc.course_address, cc.distance_km, cc.running_time_text, cc.pace_text, cc.include_route_detail, COALESCE((SELECT GROUP_CONCAT(cci.image_url ORDER BY cci.image_order SEPARATOR '\n---NEOSTRIDE-IMAGE---\n') FROM community_content_images cci WHERE cci.content_id = cc.content_id), cc.image) AS image_urls, cc.created_at,
			       COALESCE(stats.like_count, 0) AS like_count,
			       COALESCE(stats.comment_count, 0) AS comment_count,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='LIKE') AS liked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='BOOKMARK') AS bookmarked
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			LEFT JOIN community_content_stats stats ON stats.content_id=cc.content_id
			WHERE cc.content_type='TIP' AND cc.content_id=?
			""", (rs, n) -> {
			String[] parts = CommunityContentCodec.decodeTipContent(rs);
			String badge = rs.getString("badge");
			long contentId = rs.getLong("content_id");
			long authorUserId = rs.getLong("author_user_id");
			boolean mine = authorUserId == userId;
			boolean includeRouteDetail = rs.getBoolean("include_route_detail");
			boolean showRouteDetail = includeRouteDetail || mine;
			return new TipDetailResponse(contentId, authorUserId, rs.getString("nickname"), rs.getString("profile_image_url"), badgeOwned(badge), badge, fromTipType(rs.getString("tip_type")), parts[0], parts[1], includeRouteDetail, showRouteDetail ? parts[2] : null, showRouteDetail ? parts[3] : null, CommunityContentCodec.decodeImages(CommunityContentCodec.imageUrls(rs)), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getBoolean("liked"), rs.getBoolean("bookmarked"), mine, rs.getTimestamp("created_at").toLocalDateTime().format(ISO), interactionRepository.commentsForContent(userId, contentId, null, null, normalizedLimit(commentLimit)));
		}, userId, userId, tipId).stream().findFirst().orElse(null);
	}

	void updateTip(long userId, long tipId, TipUploadRequest request) {
		int updated = jdbcTemplate.update("UPDATE community_contents SET include_route_detail=?, tip_type=?, content_text=?, image=?, title=?, body_text=?, route_map_image_url=?, course_address=? WHERE content_id=? AND author_user_id=? AND content_type='TIP'", request.gpsVisible(), normalizeTipType(request.category()), CommunityContentCodec.encodeTipContent(request.title(), request.content(), request.routeMapImageUrl(), request.courseAddress()), CommunityContentCodec.encodeImages(request.imageUrls()), request.title(), request.content(), request.routeMapImageUrl(), request.courseAddress(), tipId, userId);
		if (updated > 0) {
			replaceContentImages(tipId, request.imageUrls());
		}
	}

	private List<TipUploadResponse> tipQuery(String predicate, Object... args) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, CASE WHEN u.deleted_at IS NULL THEN COALESCE(cu.community_profile_name, u.community_profile_name, u.name) ELSE '탈퇴한 사용자' END AS nickname,
			       CASE WHEN u.deleted_at IS NULL THEN COALESCE(cu.profile_photo, u.profile_photo) ELSE NULL END AS profile_image_url, CASE WHEN u.deleted_at IS NULL THEN COALESCE(cu.badge, 'NONE') ELSE 'NONE' END AS badge,
			       cc.tip_type, cc.content_text, cc.title, cc.body_text, cc.route_map_image_url, cc.course_address, cc.distance_km, cc.running_time_text, cc.pace_text, cc.include_route_detail, COALESCE((SELECT GROUP_CONCAT(cci.image_url ORDER BY cci.image_order SEPARATOR '\n---NEOSTRIDE-IMAGE---\n') FROM community_content_images cci WHERE cci.content_id = cc.content_id), cc.image) AS image_urls, cc.created_at,
			       COALESCE(stats.like_count, 0) AS like_count,
			       COALESCE(stats.comment_count, 0) AS comment_count,
			       FALSE AS liked, FALSE AS bookmarked, FALSE AS commented, FALSE AS mine
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			LEFT JOIN community_content_stats stats ON stats.content_id=cc.content_id
			WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC", this::mapTip, args);
	}

	private List<TipUploadResponse> tipQueryForViewer(String predicate, Object... args) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, CASE WHEN u.deleted_at IS NULL THEN COALESCE(cu.community_profile_name, u.community_profile_name, u.name) ELSE '탈퇴한 사용자' END AS nickname,
			       CASE WHEN u.deleted_at IS NULL THEN COALESCE(cu.profile_photo, u.profile_photo) ELSE NULL END AS profile_image_url, CASE WHEN u.deleted_at IS NULL THEN COALESCE(cu.badge, 'NONE') ELSE 'NONE' END AS badge,
			       cc.tip_type, cc.content_text, cc.title, cc.body_text, cc.route_map_image_url, cc.course_address, cc.distance_km, cc.running_time_text, cc.pace_text, cc.include_route_detail, COALESCE((SELECT GROUP_CONCAT(cci.image_url ORDER BY cci.image_order SEPARATOR '\n---NEOSTRIDE-IMAGE---\n') FROM community_content_images cci WHERE cci.content_id = cc.content_id), cc.image) AS image_urls, cc.created_at,
			       COALESCE(stats.like_count, 0) AS like_count,
			       COALESCE(stats.comment_count, 0) AS comment_count,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='LIKE') AS liked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='BOOKMARK') AS bookmarked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='COMMENT') AS commented,
			       (cc.author_user_id = ?) AS mine
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			LEFT JOIN community_content_stats stats ON stats.content_id=cc.content_id
			WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC", this::mapTip, args);
	}

	private TipUploadResponse mapTip(ResultSet rs, int n) throws SQLException {
		String[] parts = CommunityContentCodec.decodeTipContent(rs);
		String badge = rs.getString("badge");
		long writerId = rs.getLong("author_user_id");
		boolean mine = rs.getBoolean("mine");
		return new TipUploadResponse(rs.getLong("content_id"), writerId, rs.getString("nickname"), rs.getString("profile_image_url"), badgeOwned(badge), badge, fromTipType(rs.getString("tip_type")), parts[0], parts[1], rs.getBoolean("include_route_detail"), parts[2], CommunityContentCodec.decodeImages(CommunityContentCodec.imageUrls(rs)), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getBoolean("liked"), rs.getBoolean("bookmarked"), rs.getBoolean("commented"), mine, rs.getTimestamp("created_at").toLocalDateTime().format(ISO));
	}

	private List<TipUploadResponse> listTipsInteractedByType(long userId, String interactionType) {
		String normalized = interactionType == null ? null : interactionType.trim().toUpperCase(Locale.ROOT);
		switch (normalized) {
			case "LIKE", "BOOKMARK", "COMMENT" -> {
				return tipQueryForViewer(
					"cc.content_type = 'TIP' AND EXISTS (\n" +
					"\tSELECT 1 FROM community_interactions ci\n" +
					"\tWHERE ci.content_id = cc.content_id\n" +
					"\t  AND ci.interaction_type='" + normalized + "' AND ci.user_id = ?\n" +
					") AND " + blockedByCurrentUserPredicate(),
					userId, userId, userId, userId, userId, userId, userId
				);
			}
			default -> throw new IllegalArgumentException("지원하지 않는 상호작용 타입입니다.");
		}
	}

	private void insertContentImages(long contentId, List<String> images) {
		List<String> normalized = CommunityContentCodec.normalizeImages(images);
		for (int index = 0; index < normalized.size(); index++) {
			jdbcTemplate.update("INSERT INTO community_content_images (content_id, image_order, image_url) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE image_url = VALUES(image_url)", contentId, index, normalized.get(index));
		}
	}

	private void replaceContentImages(long contentId, List<String> images) {
		jdbcTemplate.update("DELETE FROM community_content_images WHERE content_id=?", contentId);
		insertContentImages(contentId, images);
	}

	private static Integer normalizedLimit(Integer limit) {
		return limit == null ? null : Math.max(1, Math.min(100, limit));
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

	private static String normalizeTipType(String category) {
		return switch ((category == null ? "FREE" : category).trim().toUpperCase(Locale.ROOT)) {
			case "TRAINING", "훈련" -> "TRAINING";
			case "COURSE", "코스" -> "COURSE";
			case "GEAR", "장비" -> "GEAR";
			case "FREE", "자유", "ETC", "" -> "ETC";
			default -> "ETC";
		};
	}

	private static String fromTipType(String tipType) {
		return "ETC".equalsIgnoreCase(tipType) || tipType == null ? "FREE" : tipType;
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
}
