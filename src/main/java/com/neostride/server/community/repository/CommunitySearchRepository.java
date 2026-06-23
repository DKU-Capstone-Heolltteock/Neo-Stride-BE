package com.neostride.server.community.repository;

import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.SearchUserResponse;
import com.neostride.server.community.dto.TipUploadResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

final class CommunitySearchRepository {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
	private static final String PUBLIC_FEED_PREDICATE = "cc.content_type = 'POST' AND cc.feed_scope = 'PUBLIC'";
	private static final String VIEWER_SCOPE_PREDICATE = "(cc.feed_scope = 'PUBLIC' OR cc.author_user_id = ? OR (cc.feed_scope = 'FRIENDS' AND EXISTS (SELECT 1 FROM relationships r WHERE r.status = 'ACCEPTED' AND ((r.user1_id = ? AND r.user2_id = cc.author_user_id) OR (r.user2_id = ? AND r.user1_id = cc.author_user_id)))))";
	private static final String VIEWER_FEED_PREDICATE = "cc.content_type = 'POST' AND " + VIEWER_SCOPE_PREDICATE;
	private static final String PROFILE_ORDER = "badge_rank DESC, friend_count DESC, u.user_id ASC";
	private static final String BADGE_RANK_SQL = """
		CASE UPPER(COALESCE(cu.badge, 'NONE'))
		    WHEN 'CHALLENGER' THEN 7 WHEN 'MASTER' THEN 6 WHEN 'DIAMOND' THEN 5
		    WHEN 'PLATINUM' THEN 4 WHEN 'GOLD' THEN 3 WHEN 'SILVER' THEN 2 WHEN 'BRONZE' THEN 1 ELSE 0 END
		""";

	private final JdbcTemplate jdbcTemplate;

	CommunitySearchRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<FeedUploadResponse> searchFeeds(String keyword, int page, int size) {
		return searchFeeds(null, keyword, page, size);
	}

	List<FeedUploadResponse> searchFeeds(Long viewerUserId, String keyword, int page, int size) {
		String predicate = viewerUserId == null ? PUBLIC_FEED_PREDICATE : VIEWER_FEED_PREDICATE;
		java.util.List<Object> args = new java.util.ArrayList<>();
		if (viewerUserId != null) {
			args.add(viewerUserId);
			args.add(viewerUserId);
			args.add(viewerUserId);
		}
		if (!blank(keyword)) {
			predicate += " AND MATCH(cc.title, cc.body_text, cc.content_text) AGAINST (? IN NATURAL LANGUAGE MODE)";
			args.add(searchTerm(keyword));
		}
		if (viewerUserId == null) {
			args.add(size);
			args.add(offset(page, size));
			return feedQueryPaged(predicate, args.toArray());
		}
		java.util.List<Object> viewerArgs = new java.util.ArrayList<>(List.of(viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId));
		viewerArgs.addAll(args);
		viewerArgs.add(viewerUserId);
		viewerArgs.add(viewerUserId);
		viewerArgs.add(size);
		viewerArgs.add(offset(page, size));
		return feedQueryPagedForViewer(predicate + " AND " + blockedByCurrentUserPredicate(), viewerArgs.toArray());
	}

	List<TipUploadResponse> searchTips(String keyword, String category, int page, int size) {
		return searchTips(null, keyword, category, page, size);
	}

	List<TipUploadResponse> searchTips(Long viewerUserId, String keyword, String category, int page, int size) {
		String predicate = "cc.content_type = 'TIP'";
		List<Object> args = new java.util.ArrayList<>();
		String normalizedCategory = normalizeSearchCategory(category);
		if (normalizedCategory != null) {
			predicate += " AND cc.tip_type = ?";
			args.add(normalizedCategory);
		}
		if (!blank(keyword)) {
			predicate += " AND MATCH(cc.title, cc.body_text, cc.content_text) AGAINST (? IN NATURAL LANGUAGE MODE)";
			args.add(searchTerm(keyword));
		}
		if (viewerUserId == null) {
			args.add(size);
			args.add(offset(page, size));
			return tipQueryPaged(predicate, args.toArray());
		}
		java.util.List<Object> viewerArgs = new java.util.ArrayList<>(List.of(viewerUserId, viewerUserId, viewerUserId, viewerUserId));
		viewerArgs.addAll(args);
		viewerArgs.add(viewerUserId);
		viewerArgs.add(viewerUserId);
		viewerArgs.add(size);
		viewerArgs.add(offset(page, size));
		return tipQueryPagedForViewer(predicate + " AND " + blockedByCurrentUserPredicate(), viewerArgs.toArray());
	}

	List<SearchUserResponse> searchProfiles(String keyword, int page, int size) {
		return searchProfiles(null, keyword, page, size);
	}

	List<SearchUserResponse> searchProfiles(Long viewerUserId, String keyword, int page, int size) {
		String predicate = "1=1";
		java.util.List<Object> args = new java.util.ArrayList<>();
		if (!blank(keyword)) {
			predicate = "(MATCH(u.name, u.community_profile_name) AGAINST (? IN NATURAL LANGUAGE MODE) OR MATCH(cu.community_profile_name, cu.status_message) AGAINST (? IN NATURAL LANGUAGE MODE))";
			args.add(searchTerm(keyword));
			args.add(searchTerm(keyword));
		}
		String filteredPredicate = predicate;
		if (viewerUserId != null) {
			filteredPredicate += " AND " + blockedUserPredicate();
			args.add(viewerUserId);
			args.add(viewerUserId);
		}
		args.add(size);
		args.add(offset(page, size));
		return viewerUserId == null ? userSearchQuery(filteredPredicate, PROFILE_ORDER, args.toArray()) : userSearchQueryForViewer(filteredPredicate, PROFILE_ORDER, args.toArray(), viewerUserId);
	}

	List<SearchUserResponse> searchFriends(long userId, String keyword) {
		String predicate = "EXISTS (SELECT 1 FROM relationships r WHERE (r.user1_id = ? AND r.user2_id = u.user_id OR r.user2_id = ? AND r.user1_id = u.user_id) AND r.status = 'ACCEPTED')";
		List<Object> args = new java.util.ArrayList<>(List.of(userId, userId));
		if (!blank(keyword)) {
			predicate += " AND (MATCH(u.name, u.community_profile_name) AGAINST (? IN NATURAL LANGUAGE MODE) OR MATCH(cu.community_profile_name, cu.status_message) AGAINST (? IN NATURAL LANGUAGE MODE))";
			args.add(searchTerm(keyword));
			args.add(searchTerm(keyword));
		}
		args.add(1000);
		args.add(0);
		return userSearchQuery(predicate, "nickname ASC, u.user_id ASC", args.toArray(), "friends");
	}

	List<SearchUserResponse> getTopProfiles(int page, int size) {
		return getTopProfiles(null, page, size);
	}

	List<SearchUserResponse> getTopProfiles(Long viewerUserId, int page, int size) {
		if (viewerUserId == null) {
			return userSearchQuery("1=1", PROFILE_ORDER, pageArgs(page, size));
		}
		return userSearchQueryForViewer("1=1 AND " + blockedUserPredicate(), PROFILE_ORDER, new Object[]{viewerUserId, viewerUserId, size, offset(page, size)}, viewerUserId);
	}

	List<SearchUserResponse> getMyFriends(long userId) {
		return searchFriends(userId, null);
	}

	private List<FeedUploadResponse> feedQueryPaged(String predicate, Object[] args) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, FALSE AS mine, COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.badge, 'NONE') AS badge, cc.created_at,
			       cc.content_text, cc.title, cc.body_text, cc.route_map_image_url, cc.course_address, cc.distance_km, cc.running_time_text, cc.pace_text, cc.include_route_detail, COALESCE((SELECT GROUP_CONCAT(cci.image_url ORDER BY cci.image_order SEPARATOR '\n---NEOSTRIDE-IMAGE---\n') FROM community_content_images cci WHERE cci.content_id = cc.content_id), cc.image) AS image_urls,
			       rr.run_record_id AS joined_running_record_id, COALESCE(rr.total_distance, 0) AS total_distance, rr.duration, rr.pace,
			       COALESCE(stats.tagged_count, 0) AS tagged_count,
			       COALESCE(stats.like_count, 0) AS like_count,
			       COALESCE(stats.comment_count, 0) AS comment_count,
			       FALSE AS liked, FALSE AS bookmarked, FALSE AS commented, FALSE AS tagged
			FROM community_contents cc
			JOIN users u ON u.user_id=cc.author_user_id
			LEFT JOIN community_users cu ON cu.user_id=u.user_id
			LEFT JOIN running_records rr ON rr.run_record_id=cc.running_record_id
			LEFT JOIN community_content_stats stats ON stats.content_id=cc.content_id
			WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC LIMIT ? OFFSET ?", (rs, n) -> mapFeed(rs), args);
	}

	private List<TipUploadResponse> tipQueryPaged(String predicate, Object[] args) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, COALESCE(cu.badge, 'NONE') AS badge,
			       cc.tip_type, cc.content_text, cc.title, cc.body_text, cc.route_map_image_url, cc.course_address, cc.distance_km, cc.running_time_text, cc.pace_text, cc.include_route_detail, COALESCE((SELECT GROUP_CONCAT(cci.image_url ORDER BY cci.image_order SEPARATOR '\n---NEOSTRIDE-IMAGE---\n') FROM community_content_images cci WHERE cci.content_id = cc.content_id), cc.image) AS image_urls, cc.created_at,
			       COALESCE(stats.like_count, 0) AS like_count,
			       COALESCE(stats.comment_count, 0) AS comment_count,
			       FALSE AS liked, FALSE AS bookmarked, FALSE AS commented, FALSE AS mine
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			LEFT JOIN community_content_stats stats ON stats.content_id=cc.content_id
			WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC LIMIT ? OFFSET ?", this::mapTip, args);
	}

	private List<FeedUploadResponse> feedQueryPagedForViewer(String predicate, Object[] args) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, (cc.author_user_id = ?) AS mine, COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.badge, 'NONE') AS badge, cc.created_at,
			       cc.content_text, cc.title, cc.body_text, cc.route_map_image_url, cc.course_address, cc.distance_km, cc.running_time_text, cc.pace_text, cc.include_route_detail, COALESCE((SELECT GROUP_CONCAT(cci.image_url ORDER BY cci.image_order SEPARATOR '\n---NEOSTRIDE-IMAGE---\n') FROM community_content_images cci WHERE cci.content_id = cc.content_id), cc.image) AS image_urls,
			       rr.run_record_id AS joined_running_record_id, COALESCE(rr.total_distance, 0) AS total_distance, rr.duration, rr.pace,
			       COALESCE(stats.tagged_count, 0) AS tagged_count,
			       COALESCE(stats.like_count, 0) AS like_count,
			       COALESCE(stats.comment_count, 0) AS comment_count,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='LIKE') AS liked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='BOOKMARK') AS bookmarked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='COMMENT') AS commented,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.tagged_user_id=? AND ci.interaction_type='TAG') AS tagged
			FROM community_contents cc
			JOIN users u ON u.user_id=cc.author_user_id
			LEFT JOIN community_users cu ON cu.user_id=u.user_id
			LEFT JOIN running_records rr ON rr.run_record_id=cc.running_record_id
			LEFT JOIN community_content_stats stats ON stats.content_id=cc.content_id
			WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC LIMIT ? OFFSET ?", (rs, n) -> mapFeed(rs), args);
	}

	private List<TipUploadResponse> tipQueryPagedForViewer(String predicate, Object[] args) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, COALESCE(cu.badge, 'NONE') AS badge,
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
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC LIMIT ? OFFSET ?", this::mapTip, args);
	}

	private FeedUploadResponse mapFeed(ResultSet rs) throws SQLException {
		DecodedFeedContent parts = CommunityContentCodec.decodeFeedContent(rs);
		long writerId = rs.getLong("author_user_id");
		String badge = rs.getString("badge");
		return new FeedUploadResponse(rs.getLong("content_id"), rs.getString("profile_image_url"), rs.getString("nickname"), badgeOwned(badge), badge, rs.getTimestamp("created_at").toLocalDateTime().format(ISO), parts.title(), parts.content(), rs.getInt("tagged_count"), rs.getInt("like_count"), rs.getInt("comment_count"), CommunityContentCodec.feedDistance(rs, parts), CommunityContentCodec.feedDuration(rs, parts), CommunityContentCodec.feedPace(rs, parts), rs.getBoolean("include_route_detail"), parts.routeMapImageUri(), CommunityContentCodec.decodeImages(CommunityContentCodec.imageUrls(rs)), rs.getBoolean("liked"), rs.getBoolean("bookmarked"), rs.getBoolean("commented"), rs.getBoolean("tagged"), rs.getBoolean("mine"), writerId);
	}

	private TipUploadResponse mapTip(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		String[] parts = CommunityContentCodec.decodeTipContent(rs);
		String badge = rs.getString("badge");
		long writerId = rs.getLong("author_user_id");
		boolean mine = rs.getBoolean("mine");
		return new TipUploadResponse(rs.getLong("content_id"), writerId, rs.getString("nickname"), rs.getString("profile_image_url"), badgeOwned(badge), badge, fromTipType(rs.getString("tip_type")), parts[0], parts[1], rs.getBoolean("include_route_detail"), parts[2], CommunityContentCodec.decodeImages(CommunityContentCodec.imageUrls(rs)), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getBoolean("liked"), rs.getBoolean("bookmarked"), rs.getBoolean("commented"), mine, rs.getTimestamp("created_at").toLocalDateTime().format(ISO));
	}

	private List<SearchUserResponse> userSearchQuery(String predicate, String orderBy, Object[] args) {
		return userSearchQuery(predicate, orderBy, args, "none");
	}

	private List<SearchUserResponse> userSearchQueryForViewer(String predicate, String orderBy, Object[] args, long viewerUserId) {
		Object[] queryArgs = prependArgs(args, viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId);
		return jdbcTemplate.query("""
			SELECT u.user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       cu.status_message,
			       COALESCE(cu.badge, 'NONE') AS badge_tier,
			       %s AS badge_rank,
			       (SELECT COUNT(*) FROM relationships rf WHERE (rf.user1_id = u.user_id OR rf.user2_id = u.user_id) AND rf.status='ACCEPTED') AS friend_count,
			       CASE
			           WHEN EXISTS (SELECT 1 FROM relationships r WHERE r.status='BLOCKED' AND r.user1_id=? AND r.user2_id=u.user_id) THEN 'blocked'
			           WHEN EXISTS (SELECT 1 FROM relationships r WHERE r.status='ACCEPTED' AND ((r.user1_id=? AND r.user2_id=u.user_id) OR (r.user2_id=? AND r.user1_id=u.user_id))) THEN 'friends'
			           WHEN EXISTS (SELECT 1 FROM relationships r WHERE r.status='REQUESTED' AND r.user1_id=? AND r.user2_id=u.user_id) THEN 'sent'
			           WHEN EXISTS (SELECT 1 FROM relationships r WHERE r.status='REQUESTED' AND r.user2_id=? AND r.user1_id=u.user_id) THEN 'received'
			           ELSE 'none'
			       END AS relationship_status
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id
			WHERE
			""".formatted(BADGE_RANK_SQL) + predicate + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?", (rs, n) -> new SearchUserResponse(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("profile_image_url"), rs.getString("status_message"), rs.getInt("friend_count"), rs.getString("badge_tier"), rs.getString("relationship_status")), queryArgs);
	}

	private List<SearchUserResponse> userSearchQuery(String predicate, String orderBy, Object[] args, String status) {
		return jdbcTemplate.query("""
			SELECT u.user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       cu.status_message,
			       COALESCE(cu.badge, 'NONE') AS badge_tier,
			       %s AS badge_rank,
			       (SELECT COUNT(*) FROM relationships rf WHERE (rf.user1_id = u.user_id OR rf.user2_id = u.user_id) AND rf.status='ACCEPTED') AS friend_count
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id
			WHERE
			""".formatted(BADGE_RANK_SQL) + predicate + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?", (rs, n) -> new SearchUserResponse(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("profile_image_url"), rs.getString("status_message"), rs.getInt("friend_count"), rs.getString("badge_tier"), status), args);
	}


	private static Object[] prependArgs(Object[] args, Object... prefix) {
		Object[] merged = new Object[prefix.length + args.length];
		System.arraycopy(prefix, 0, merged, 0, prefix.length);
		System.arraycopy(args, 0, merged, prefix.length, args.length);
		return merged;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static String searchTerm(String value) {
		return value == null ? "" : value.trim();
	}

	private static int offset(int page, int size) {
		return page * size;
	}

	private static Object[] pageArgs(int page, int size) {
		return new Object[]{size, offset(page, size)};
	}

	private static String normalizeSearchCategory(String category) {
		String value = category == null ? "ALL" : category.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (value) {
			case "", "ALL" -> null;
			case "FREE", "자유", "ETC" -> "ETC";
			case "TRAINING", "훈련" -> "TRAINING";
			case "COURSE", "코스" -> "COURSE";
			case "GEAR", "장비" -> "GEAR";
			default -> value;
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

	private static String blockedUserPredicate() {
		return "NOT EXISTS (" +
			"SELECT 1 FROM relationships r " +
			"WHERE r.status = 'BLOCKED' " +
			"AND ((r.user1_id = ? AND r.user2_id = u.user_id) " +
			"OR (r.user2_id = ? AND r.user1_id = u.user_id))" +
		")";
	}
}
