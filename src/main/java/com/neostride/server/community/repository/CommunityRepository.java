package com.neostride.server.community.repository;

import com.neostride.server.community.dto.BadgeDetailResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendRequest;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.community.dto.AccountInfoResponse;
import com.neostride.server.community.dto.SearchUserResponse;
import com.neostride.server.community.dto.TipUploadRequest;
import com.neostride.server.community.dto.TipUploadResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CommunityRepository {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
	private final JdbcTemplate jdbcTemplate;

	public CommunityRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	public UserProfileResponse getUserProfile(long userId) {
		List<UserProfileResponse> rows = jdbcTemplate.query("""
			SELECT COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_photo,
			       cu.status_message,
			       (SELECT COUNT(*) FROM relationships r WHERE (r.user1_id = u.user_id OR r.user2_id = u.user_id) AND r.status = 'ACCEPTED') AS friend_count,
			       (SELECT COUNT(*) FROM community_contents cc WHERE cc.author_user_id = u.user_id) AS post_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.tagged_user_id = u.user_id AND ci.interaction_type = 'TAG') AS tagged_count,
			       (SELECT COUNT(DISTINCT ci.content_id) FROM community_interactions ci WHERE ci.user_id = u.user_id AND ci.interaction_type = 'COMMENT') AS commented_feed_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.user_id = u.user_id AND ci.interaction_type = 'LIKE') AS liked_feed_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.user_id = u.user_id AND ci.interaction_type = 'BOOKMARK') AS bookmarked_feed_count
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id WHERE u.user_id = ?
			""", (rs, n) -> new UserProfileResponse(rs.getString("nickname"), rs.getString("profile_photo"), rs.getString("status_message"), rs.getInt("friend_count"), rs.getInt("post_count"), rs.getInt("tagged_count"), rs.getInt("commented_feed_count"), rs.getInt("liked_feed_count"), rs.getInt("bookmarked_feed_count")), userId);
		return rows.isEmpty() ? new UserProfileResponse(null, null, null, 0, 0, 0, 0, 0, 0) : rows.getFirst();
	}

	public AccountInfoResponse getAccountInfo(long userId) {
		List<AccountInfoResponse> rows = jdbcTemplate.query("""
			SELECT email, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_photo
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id WHERE u.user_id = ?
			""", (rs, n) -> new AccountInfoResponse(rs.getString("email"), rs.getString("nickname"), rs.getString("profile_photo")), userId);
		return rows.isEmpty() ? new AccountInfoResponse(null, null, null) : rows.getFirst();
	}

	public void updateStatusMessage(long userId, String statusMessage) {
		jdbcTemplate.update("""
			INSERT INTO community_users (user_id, community_profile_name, profile_photo, status_message)
			SELECT user_id, COALESCE(community_profile_name, name), profile_photo, ? FROM users WHERE user_id = ?
			ON DUPLICATE KEY UPDATE status_message = VALUES(status_message)
			""", statusMessage, userId);
	}

	public void updateNickname(long userId, String nickname) {
		jdbcTemplate.update("UPDATE users SET community_profile_name = ? WHERE user_id = ?", nickname, userId);
		jdbcTemplate.update("""
			INSERT INTO community_users (user_id, community_profile_name, profile_photo, status_message)
			SELECT user_id, COALESCE(?, community_profile_name, name), profile_photo, NULL FROM users WHERE user_id = ?
			ON DUPLICATE KEY UPDATE community_profile_name = VALUES(community_profile_name)
			""", nickname, userId);
	}

	public void deleteAccount(long userId) {
		jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
	}

	public void updateProfileImage(long userId, String profileImageUrl) {
		jdbcTemplate.update("""
			INSERT INTO community_users (user_id, community_profile_name, profile_photo, status_message)
			SELECT user_id, COALESCE(community_profile_name, name), ?, NULL FROM users WHERE user_id = ?
			ON DUPLICATE KEY UPDATE profile_photo = VALUES(profile_photo)
			""", profileImageUrl, userId);
		jdbcTemplate.update("UPDATE users SET profile_photo = ? WHERE user_id = ?", profileImageUrl, userId);
	}

	public List<CommunityContentResponse> myFeeds(long userId) { return contentQuery("cc.author_user_id = ?", userId); }
	public List<CommunityContentResponse> taggedFeeds(long userId) { return contentQuery("EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id = cc.content_id AND ci.interaction_type='TAG' AND ci.tagged_user_id = ?)", userId); }
	public List<CommunityContentResponse> interactedFeeds(long userId, String type) { return contentQuery("EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id = cc.content_id AND ci.interaction_type='" + type + "' AND ci.user_id = ?)", userId); }

	private List<CommunityContentResponse> contentQuery(String predicate, long userId) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.content_text, COALESCE(rr.total_distance, 0) AS total_distance, rr.duration, rr.pace, cc.created_at
			FROM community_contents cc LEFT JOIN running_records rr ON rr.run_record_id = cc.running_record_id
			WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC", (rs, n) -> new CommunityContentResponse(rs.getLong("content_id"), rs.getString("content_text"), rs.getBigDecimal("total_distance"), nullableInt(rs.getObject("duration")), nullableInt(rs.getObject("pace")), rs.getTimestamp("created_at").toLocalDateTime().format(ISO)), userId);
	}

	public boolean toggleBookmark(long userId, long contentId) {
		Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_interactions WHERE user_id=? AND content_id=? AND interaction_type='BOOKMARK'", Integer.class, userId, contentId);
		if (existing != null && existing > 0) {
			jdbcTemplate.update("DELETE FROM community_interactions WHERE user_id=? AND content_id=? AND interaction_type='BOOKMARK'", userId, contentId);
			return false;
		}
		jdbcTemplate.update("INSERT INTO community_interactions (user_id, content_id, interaction_type) VALUES (?, ?, 'BOOKMARK')", userId, contentId);
		return true;
	}

	public BadgeDetailResponse getBadgeDetail(long userId) {
		List<BadgeDetailResponse> rows = jdbcTemplate.query("""
			SELECT COALESCE(cu.badge, 'NONE') AS badge, rr.run_record_id, rr.total_distance, rr.pace, rr.created_at
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id
			LEFT JOIN running_records rr ON rr.user_id = u.user_id
			WHERE u.user_id = ? ORDER BY rr.total_distance DESC, rr.created_at DESC LIMIT 1
			""", (rs, n) -> new BadgeDetailResponse(rs.getString("badge"), nullableLong(rs.getObject("run_record_id")), nullToZero(rs.getBigDecimal("total_distance")), rs.getObject("pace") == null ? null : String.valueOf(rs.getInt("pace")), rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime().format(ISO)), userId);
		return rows.isEmpty() ? new BadgeDetailResponse("NONE", null, BigDecimal.ZERO, null, null) : rows.getFirst();
	}

	public List<FriendResponse> getFriendList(long userId, String status) {
		String dbStatus = toRelationshipStatus(status);
		return jdbcTemplate.query("""
			SELECT other_user.user_id, COALESCE(cu.community_profile_name, other_user.community_profile_name, other_user.name) AS nickname,
			       COALESCE(cu.badge, 'NONE') AS badge_tier,
			       (SELECT COUNT(*) FROM relationships rf WHERE (rf.user1_id = other_user.user_id OR rf.user2_id = other_user.user_id) AND rf.status='ACCEPTED') AS friend_count,
			       COALESCE(cu.profile_photo, other_user.profile_photo) AS profile_image_url, r.status
			FROM relationships r
			JOIN users other_user ON other_user.user_id = CASE WHEN r.user1_id = ? THEN r.user2_id ELSE r.user1_id END
			LEFT JOIN community_users cu ON cu.user_id = other_user.user_id
			WHERE (r.user1_id = ? OR r.user2_id = ?) AND r.status = ?
			ORDER BY other_user.user_id
			""", (rs, n) -> new FriendResponse(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("badge_tier"), rs.getInt("friend_count"), rs.getString("profile_image_url"), rs.getString("status").toLowerCase(Locale.ROOT)), userId, userId, userId, dbStatus);
	}

	public void updateRelationship(long userId, FriendRequest request) {
		if (request == null) throw new IllegalArgumentException("요청 본문이 필요합니다.");
		if (request.targetId() == null || request.targetId() <= 0 || request.targetId() == userId) throw new IllegalArgumentException("target_id가 올바르지 않습니다.");
		switch ((request.action() == null ? "" : request.action()).toLowerCase(Locale.ROOT)) {
			case "request" -> jdbcTemplate.update("INSERT INTO relationships (user1_id, user2_id, status) VALUES (?, ?, 'REQUESTED') ON DUPLICATE KEY UPDATE status = 'REQUESTED'", userId, request.targetId());
			case "accept" -> jdbcTemplate.update("UPDATE relationships SET status='ACCEPTED' WHERE user1_id=? AND user2_id=?", request.targetId(), userId);
			case "reject" -> jdbcTemplate.update("UPDATE relationships SET status='REJECTED' WHERE user1_id=? AND user2_id=?", request.targetId(), userId);
			case "block" -> jdbcTemplate.update("INSERT INTO relationships (user1_id, user2_id, status) VALUES (?, ?, 'BLOCKED') ON DUPLICATE KEY UPDATE status='BLOCKED'", userId, request.targetId());
			case "cancel" -> jdbcTemplate.update("DELETE FROM relationships WHERE user1_id=? AND user2_id=? AND status='REQUESTED'", userId, request.targetId());
			default -> throw new IllegalArgumentException("지원하지 않는 relationship action입니다.");
		}
	}

	public long insertFeed(long userId, FeedUploadRequest request) {
		KeyHolder kh = new GeneratedKeyHolder();
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement("""
				INSERT INTO community_contents (author_user_id, include_route_detail, content_type, feed_scope, content_text, image)
				VALUES (?, ?, 'POST', ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, userId); ps.setBoolean(2, request.mapVisible()); ps.setString(3, normalizeScope(request.privacy())); ps.setString(4, encodeFeedContent(request.title(), request.content(), request.routeMapImageUri())); ps.setString(5, firstImage(request.imageUrls())); return ps;
		}, kh);
		long contentId = generatedKey(kh, "피드 ID를 생성하지 못했습니다.");
		if (request.taggedUserIds() != null) for (Long tagged : request.taggedUserIds()) if (tagged != null) jdbcTemplate.update("INSERT INTO community_interactions (user_id, content_id, interaction_type, tagged_user_id) VALUES (?, ?, 'TAG', ?)", userId, contentId, tagged);
		return contentId;
	}

	public FeedUploadResponse findFeed(long feedId) { return feedQuery("cc.content_id = ?", feedId).stream().findFirst().orElse(null); }
	public List<FeedUploadResponse> listFeeds() { return feedQuery("cc.feed_scope <> 'PRIVATE'", null); }

	public long insertTip(long userId, TipUploadRequest request) {
		KeyHolder kh = new GeneratedKeyHolder();
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement("""
				INSERT INTO community_contents (author_user_id, include_route_detail, content_type, tip_type, feed_scope, content_text, image)
				VALUES (?, ?, 'TIP', ?, 'PUBLIC', ?, ?)
				""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, userId);
			ps.setBoolean(2, request != null && request.gpsVisible());
			ps.setString(3, normalizeTipType(request == null ? null : request.category()));
			ps.setString(4, encodeTipContent(request == null ? null : request.title(), request == null ? null : request.content(), request == null ? null : request.routeMapImageUrl()));
			ps.setString(5, firstImage(request == null ? null : request.imageUrls()));
			return ps;
		}, kh);
		return generatedKey(kh, "팁 ID를 생성하지 못했습니다.");
	}

	public TipUploadResponse findTip(long tipId) { return tipQuery("cc.content_id = ?", tipId).stream().findFirst().orElse(null); }
	public List<TipUploadResponse> listTips() { return tipQuery("cc.content_type = 'TIP'", null); }

	public List<FeedUploadResponse> searchFeeds(String keyword, int page, int size) {
		String predicate = "cc.content_type = 'POST' AND cc.feed_scope <> 'PRIVATE'";
		Object[] args = pageArgs(page, size);
		if (!blank(keyword)) {
			predicate += " AND LOWER(cc.content_text) LIKE ?";
			args = new Object[]{like(keyword), size, offset(page, size)};
		}
		return feedQueryPaged(predicate, args);
	}

	public List<TipUploadResponse> searchTips(String keyword, String category, int page, int size) {
		String predicate = "cc.content_type = 'TIP'";
		List<Object> args = new java.util.ArrayList<>();
		String normalizedCategory = normalizeSearchCategory(category);
		if (normalizedCategory != null) {
			predicate += " AND cc.tip_type = ?";
			args.add(normalizedCategory);
		}
		if (!blank(keyword)) {
			predicate += " AND LOWER(cc.content_text) LIKE ?";
			args.add(like(keyword));
		}
		args.add(size);
		args.add(offset(page, size));
		return tipQueryPaged(predicate, args.toArray());
	}

	public List<SearchUserResponse> searchProfiles(String keyword, int page, int size) {
		String predicate = "1=1";
		Object[] args = pageArgs(page, size);
		if (!blank(keyword)) {
			predicate = "LOWER(COALESCE(cu.community_profile_name, u.community_profile_name, u.name)) LIKE ?";
			args = new Object[]{like(keyword), size, offset(page, size)};
		}
		return userSearchQuery(predicate, "badge_rank DESC, friend_count DESC, u.user_id ASC", args);
	}

	public List<SearchUserResponse> searchFriends(long userId, String keyword) {
		String predicate = "EXISTS (SELECT 1 FROM relationships r WHERE (r.user1_id = ? AND r.user2_id = u.user_id OR r.user2_id = ? AND r.user1_id = u.user_id) AND r.status = 'ACCEPTED')";
		List<Object> args = new java.util.ArrayList<>(List.of(userId, userId));
		if (!blank(keyword)) {
			predicate += " AND LOWER(COALESCE(cu.community_profile_name, u.community_profile_name, u.name)) LIKE ?";
			args.add(like(keyword));
		}
		args.add(1000);
		args.add(0);
		return userSearchQuery(predicate, "nickname ASC, u.user_id ASC", args.toArray());
	}

	public List<SearchUserResponse> getTopProfiles(int page, int size) {
		return userSearchQuery("1=1", "badge_rank DESC, friend_count DESC, u.user_id ASC", pageArgs(page, size));
	}

	public List<SearchUserResponse> getMyFriends(long userId) {
		return searchFriends(userId, null);
	}

	private List<FeedUploadResponse> feedQuery(String predicate, Long id) {
		Object[] args = id == null ? new Object[]{} : new Object[]{id};
		return jdbcTemplate.query("""
			SELECT cc.content_id, COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname, cc.created_at,
			       cc.content_text, cc.include_route_detail, cc.image,
			       COALESCE(rr.total_distance, 0) AS total_distance, rr.duration, rr.pace,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='TAG') AS tagged_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='LIKE') AS like_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='COMMENT') AS comment_count
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			LEFT JOIN running_records rr ON rr.run_record_id=cc.running_record_id WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC", (rs, n) -> mapFeed(rs), args);
	}

	private FeedUploadResponse mapFeed(java.sql.ResultSet rs) throws java.sql.SQLException {
		String[] parts = decodeFeedContent(rs.getString("content_text"));
		return new FeedUploadResponse(rs.getLong("content_id"), rs.getString("profile_image_url"), rs.getString("nickname"), rs.getTimestamp("created_at").toLocalDateTime().format(ISO), parts[0], parts[1], rs.getInt("tagged_count"), rs.getInt("like_count"), rs.getInt("comment_count"), String.format(Locale.KOREA, "%.2f km", rs.getBigDecimal("total_distance")), rs.getObject("duration") == null ? null : String.valueOf(rs.getInt("duration")), rs.getObject("pace") == null ? null : String.valueOf(rs.getInt("pace")), rs.getBoolean("include_route_detail"), parts[2], rs.getString("image") == null ? List.of() : List.of(rs.getString("image")));
	}

	private List<TipUploadResponse> tipQuery(String predicate, Long id) {
		Object[] args = id == null ? new Object[]{} : new Object[]{id};
		return jdbcTemplate.query("""
			SELECT cc.content_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, COALESCE(cu.badge, 'NONE') AS badge,
			       cc.tip_type, cc.content_text, cc.include_route_detail, cc.image, cc.created_at,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='LIKE') AS like_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='COMMENT') AS comment_count
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC", this::mapTip, args);
	}

	private List<FeedUploadResponse> feedQueryPaged(String predicate, Object[] args) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname, cc.created_at,
			       cc.content_text, cc.include_route_detail, cc.image,
			       COALESCE(rr.total_distance, 0) AS total_distance, rr.duration, rr.pace,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='TAG') AS tagged_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='LIKE') AS like_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='COMMENT') AS comment_count
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			LEFT JOIN running_records rr ON rr.run_record_id=cc.running_record_id WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC LIMIT ? OFFSET ?", (rs, n) -> mapFeed(rs), args);
	}

	private List<TipUploadResponse> tipQueryPaged(String predicate, Object[] args) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, COALESCE(cu.badge, 'NONE') AS badge,
			       cc.tip_type, cc.content_text, cc.include_route_detail, cc.image, cc.created_at,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='LIKE') AS like_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='COMMENT') AS comment_count
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC LIMIT ? OFFSET ?", this::mapTip, args);
	}

	private TipUploadResponse mapTip(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		String[] parts = decodeTipContent(rs.getString("content_text"));
		return new TipUploadResponse(rs.getLong("content_id"), rs.getString("nickname"), rs.getString("profile_image_url"), !"NONE".equalsIgnoreCase(rs.getString("badge")), fromTipType(rs.getString("tip_type")), parts[0], parts[1], rs.getBoolean("include_route_detail"), parts[2], rs.getString("image") == null ? List.of() : List.of(rs.getString("image")), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getTimestamp("created_at").toLocalDateTime().format(ISO));
	}

	private List<SearchUserResponse> userSearchQuery(String predicate, String orderBy, Object[] args) {
		return jdbcTemplate.query("""
			SELECT u.user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       cu.status_message,
			       COALESCE(cu.badge, 'NONE') AS badge_tier,
			       CASE UPPER(COALESCE(cu.badge, 'NONE'))
			           WHEN 'CHALLENGER' THEN 7 WHEN 'MASTER' THEN 6 WHEN 'DIAMOND' THEN 5
			           WHEN 'PLATINUM' THEN 4 WHEN 'GOLD' THEN 3 WHEN 'SILVER' THEN 2 WHEN 'BRONZE' THEN 1 ELSE 0 END AS badge_rank,
			       (SELECT COUNT(*) FROM relationships rf WHERE (rf.user1_id = u.user_id OR rf.user2_id = u.user_id) AND rf.status='ACCEPTED') AS friend_count
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id
			WHERE
			""" + predicate + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?", (rs, n) -> new SearchUserResponse(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("profile_image_url"), rs.getString("status_message"), rs.getInt("friend_count"), rs.getString("badge_tier"), "none"), args);
	}

	private static String toRelationshipStatus(String status) { return switch ((status == null ? "friends" : status).toLowerCase(Locale.ROOT)) { case "sent", "received" -> "REQUESTED"; case "blocked" -> "BLOCKED"; default -> "ACCEPTED"; }; }
	private static boolean blank(String value) { return value == null || value.isBlank(); }
	private static String like(String value) { return "%" + value.trim().toLowerCase(Locale.ROOT) + "%"; }
	private static int offset(int page, int size) { return page * size; }
	private static Object[] pageArgs(int page, int size) { return new Object[]{size, offset(page, size)}; }
	private static String normalizeSearchCategory(String category) { String value = category == null ? "ALL" : category.trim().toUpperCase(Locale.ROOT); return switch (value) { case "", "ALL", "FREE" -> null; case "TRAINING" -> "TRAINING"; case "COURSE", "코스" -> "COURSE"; case "GEAR", "장비" -> "GEAR"; default -> value; }; }
	private static String normalizeScope(String privacy) { return switch ((privacy == null ? "PUBLIC" : privacy).toUpperCase(Locale.ROOT)) { case "FRIENDS" -> "FRIENDS"; case "PRIVATE" -> "PRIVATE"; default -> "PUBLIC"; }; }
	private static String normalizeTipType(String category) { return switch ((category == null ? "ETC" : category).toUpperCase(Locale.ROOT)) { case "TRAINING" -> "TRAINING"; case "COURSE", "코스" -> "COURSE"; case "GEAR", "장비" -> "GEAR"; default -> "ETC"; }; }
	private static String fromTipType(String tipType) { return tipType == null ? "ETC" : tipType; }
	private static String encodeFeedContent(String title, String content, String routeMapImageUrl) { return (title == null ? "" : title) + "\n---NEOSTRIDE-FEED---\n" + (content == null ? "" : content) + "\n---NEOSTRIDE-ROUTE---\n" + (routeMapImageUrl == null ? "" : routeMapImageUrl); }
	private static String[] decodeFeedContent(String raw) { String[] first = (raw == null ? "" : raw).split("\\n---NEOSTRIDE-FEED---\\n", 2); if (first.length == 1) return new String[]{null, first[0], null}; String rest = first[1]; String[] second = rest.split("\\n---NEOSTRIDE-ROUTE---\\n", 2); return new String[]{first[0], second.length > 0 ? second[0] : rest, second.length > 1 && !second[1].isBlank() ? second[1] : null}; }
	private static String encodeTipContent(String title, String content, String routeMapImageUrl) { return (title == null ? "" : title) + "\n---NEOSTRIDE-TIP---\n" + (content == null ? "" : content) + "\n---NEOSTRIDE-ROUTE---\n" + (routeMapImageUrl == null ? "" : routeMapImageUrl); }
	private static String[] decodeTipContent(String raw) { String[] first = (raw == null ? "" : raw).split("\\n---NEOSTRIDE-TIP---\\n", 2); String title = first.length > 0 ? first[0] : ""; String rest = first.length > 1 ? first[1] : ""; String[] second = rest.split("\\n---NEOSTRIDE-ROUTE---\\n", 2); return new String[]{title, second.length > 0 ? second[0] : rest, second.length > 1 && !second[1].isBlank() ? second[1] : null}; }
	private static String firstImage(List<String> images) { return images == null || images.isEmpty() ? null : images.getFirst(); }
	private static Integer nullableInt(Object value) { return value == null ? null : ((Number) value).intValue(); }
	private static Long nullableLong(Object value) { return value == null ? null : ((Number) value).longValue(); }
	private static BigDecimal nullToZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
	private static long generatedKey(KeyHolder keyHolder, String message) { Number key = keyHolder.getKey(); if (key == null) throw new IllegalStateException(message); return key.longValue(); }
}

