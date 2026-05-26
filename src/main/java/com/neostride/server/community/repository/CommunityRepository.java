package com.neostride.server.community.repository;

import com.neostride.server.community.dto.*;
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
	private static final String IMAGE_DELIMITER = "\n---NEOSTRIDE-IMAGE---\n";
	private final JdbcTemplate jdbcTemplate;

	public CommunityRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	public UserProfileResponse getUserProfile(long userId) {
		List<UserProfileResponse> rows = jdbcTemplate.query("""
			SELECT COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_photo,
			       cu.status_message,
			       (SELECT COUNT(*) FROM relationships r WHERE (r.user1_id = u.user_id OR r.user2_id = u.user_id) AND r.status = 'ACCEPTED') AS friend_count,
			       (SELECT COUNT(*) FROM community_contents cc WHERE cc.author_user_id = u.user_id AND cc.content_type = 'POST') AS post_count,
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
			""" + feedPredicate(predicate) + " ORDER BY cc.created_at DESC, cc.content_id DESC", (rs, n) -> new CommunityContentResponse(rs.getLong("content_id"), rs.getString("content_text"), rs.getBigDecimal("total_distance"), nullableInt(rs.getObject("duration")), nullableInt(rs.getObject("pace")), rs.getTimestamp("created_at").toLocalDateTime().format(ISO)), userId);
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
			ps.setLong(1, userId); ps.setBoolean(2, request.mapVisible()); ps.setString(3, normalizeScope(request.privacy())); ps.setString(4, encodeFeedContent(request.title(), request.content(), request.routeMapImageUri())); ps.setString(5, encodeImages(request.imageUrls())); return ps;
		}, kh);
		long contentId = generatedKey(kh, "피드 ID를 생성하지 못했습니다.");
		if (request.taggedUserIds() != null) for (Long tagged : request.taggedUserIds()) if (tagged != null) jdbcTemplate.update("INSERT INTO community_interactions (user_id, content_id, interaction_type, tagged_user_id) VALUES (?, ?, 'TAG', ?)", userId, contentId, tagged);
		return contentId;
	}

	public FeedUploadResponse findFeed(long feedId) { return feedQuery("cc.content_id = ?", feedId).stream().findFirst().orElse(null); }
	public List<FeedUploadResponse> listFeeds() { return feedQuery("cc.content_type = 'POST' AND cc.feed_scope <> 'PRIVATE'", null); }

	public FeedDetailResponse findFeedDetail(long userId, long feedId) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.badge, 'NONE') AS badge, cc.created_at, cc.content_text, cc.include_route_detail, cc.image,
			       COALESCE(rr.total_distance, 0) AS total_distance, rr.duration, rr.pace,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='TAG') AS tagged_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='LIKE') AS like_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='COMMENT') AS comment_count,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='LIKE') AS liked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='BOOKMARK') AS bookmarked
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			LEFT JOIN running_records rr ON rr.run_record_id=cc.running_record_id WHERE cc.content_type='POST' AND cc.content_id=?
			""", (rs, n) -> {
			String[] parts = decodeFeedContent(rs.getString("content_text"));
			String badge = rs.getString("badge");
			long contentId = rs.getLong("content_id");
			return new FeedDetailResponse(contentId, rs.getLong("author_user_id"), rs.getString("profile_image_url"), rs.getString("nickname"), badgeOwned(badge), badge, rs.getTimestamp("created_at").toLocalDateTime().format(ISO), parts[0], parts[1], rs.getInt("tagged_count"), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getBoolean("liked"), rs.getBoolean("bookmarked"), rs.getLong("author_user_id") == userId, String.format(Locale.KOREA, "%.2f km", rs.getBigDecimal("total_distance")), rs.getObject("duration") == null ? null : String.valueOf(rs.getInt("duration")), rs.getObject("pace") == null ? null : String.valueOf(rs.getInt("pace")), rs.getBoolean("include_route_detail"), parts[2], decodeImages(rs.getString("image")), commentsForContent(userId, contentId));
		}, userId, userId, feedId).stream().findFirst().orElse(null);
	}

	public boolean toggleInteraction(long userId, long contentId, String type) {
		Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_interactions WHERE user_id=? AND content_id=? AND interaction_type=?", Integer.class, userId, contentId, type);
		if (existing != null && existing > 0) {
			jdbcTemplate.update("DELETE FROM community_interactions WHERE user_id=? AND content_id=? AND interaction_type=?", userId, contentId, type);
			return false;
		}
		jdbcTemplate.update("INSERT INTO community_interactions (user_id, content_id, interaction_type) VALUES (?, ?, ?)", userId, contentId, type);
		return true;
	}

	public CommentResponse createComment(long userId, long contentId, CommentRequest request) {
		KeyHolder kh = new GeneratedKeyHolder();
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement("INSERT INTO community_interactions (user_id, content_id, interaction_type, comment_text) VALUES (?, ?, 'COMMENT', ?)", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, userId);
			ps.setLong(2, contentId);
			ps.setString(3, request.content());
			return ps;
		}, kh);
		return findComment(userId, generatedKey(kh, "댓글 ID를 생성하지 못했습니다."));
	}

	public CommentResponse updateComment(long userId, long contentId, long commentId, CommentRequest request) {
		jdbcTemplate.update("UPDATE community_interactions SET comment_text=? WHERE interaction_id=? AND user_id=? AND content_id=? AND interaction_type='COMMENT'", request.content(), commentId, userId, contentId);
		return findComment(userId, commentId);
	}

	public void deleteComment(long userId, long contentId, long commentId) {
		jdbcTemplate.update("DELETE FROM community_interactions WHERE interaction_id=? AND user_id=? AND content_id=? AND interaction_type='COMMENT'", commentId, userId, contentId);
	}

	public void updateFeed(long userId, long feedId, FeedUploadRequest request) {
		jdbcTemplate.update("UPDATE community_contents SET include_route_detail=?, feed_scope=?, content_text=?, image=? WHERE content_id=? AND author_user_id=? AND content_type='POST'", request.mapVisible(), normalizeScope(request.privacy()), encodeFeedContent(request.title(), request.content(), request.routeMapImageUri()), encodeImages(request.imageUrls()), feedId, userId);
	}

	public void deleteContent(long userId, long contentId, String contentType) {
		jdbcTemplate.update("DELETE FROM community_interactions WHERE content_id=?", contentId);
		jdbcTemplate.update("DELETE FROM community_contents WHERE content_id=? AND author_user_id=? AND content_type=?", contentId, userId, contentType);
	}

	public List<FriendResponse> getTaggedUsers(long feedId) {
		return jdbcTemplate.query("""
			SELECT u.user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.badge, 'NONE') AS badge_tier,
			       (SELECT COUNT(*) FROM relationships rf WHERE (rf.user1_id = u.user_id OR rf.user2_id = u.user_id) AND rf.status='ACCEPTED') AS friend_count,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url
			FROM community_interactions ci JOIN users u ON u.user_id=ci.tagged_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE ci.content_id=? AND ci.interaction_type='TAG' ORDER BY u.user_id
			""", (rs, n) -> new FriendResponse(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("badge_tier"), rs.getInt("friend_count"), rs.getString("profile_image_url"), "tagged"), feedId);
	}

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
			ps.setString(5, encodeImages(request == null ? null : request.imageUrls()));
			return ps;
		}, kh);
		return generatedKey(kh, "팁 ID를 생성하지 못했습니다.");
	}

	public TipUploadResponse findTip(long tipId) { return tipQuery("cc.content_id = ?", tipId).stream().findFirst().orElse(null); }
	public List<TipUploadResponse> listTips() { return tipQuery("cc.content_type = 'TIP'", null); }
	public List<TipUploadResponse> listTips(long viewerUserId) { return tipQueryForViewer("cc.content_type = 'TIP'", new Object[]{viewerUserId, viewerUserId, viewerUserId, viewerUserId}); }
	public List<TipUploadResponse> listTipsByUser(long userId) { return tipQuery("cc.content_type = 'TIP' AND cc.author_user_id = ?", userId); }

	public TipDetailResponse findTipDetail(long userId, long tipId) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, COALESCE(cu.badge, 'NONE') AS badge,
			       cc.tip_type, cc.content_text, cc.include_route_detail, cc.image, cc.created_at,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='LIKE') AS like_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='COMMENT') AS comment_count,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='LIKE') AS liked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='BOOKMARK') AS bookmarked
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE cc.content_type='TIP' AND cc.content_id=?
			""", (rs, n) -> {
			String[] parts = decodeTipContent(rs.getString("content_text"));
			String badge = rs.getString("badge");
			long contentId = rs.getLong("content_id");
			return new TipDetailResponse(contentId, rs.getLong("author_user_id"), rs.getString("nickname"), rs.getString("profile_image_url"), badgeOwned(badge), badge, fromTipType(rs.getString("tip_type")), parts[0], parts[1], rs.getBoolean("include_route_detail"), parts[2], null, decodeImages(rs.getString("image")), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getBoolean("liked"), rs.getBoolean("bookmarked"), rs.getLong("author_user_id") == userId, rs.getTimestamp("created_at").toLocalDateTime().format(ISO), commentsForContent(userId, contentId));
		}, userId, userId, tipId).stream().findFirst().orElse(null);
	}

	public void updateTip(long userId, long tipId, TipUploadRequest request) {
		jdbcTemplate.update("UPDATE community_contents SET include_route_detail=?, tip_type=?, content_text=?, image=? WHERE content_id=? AND author_user_id=? AND content_type='TIP'", request.gpsVisible(), normalizeTipType(request.category()), encodeTipContent(request.title(), request.content(), request.routeMapImageUrl()), encodeImages(request.imageUrls()), tipId, userId);
	}

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
		return new FeedUploadResponse(rs.getLong("content_id"), rs.getString("profile_image_url"), rs.getString("nickname"), rs.getTimestamp("created_at").toLocalDateTime().format(ISO), parts[0], parts[1], rs.getInt("tagged_count"), rs.getInt("like_count"), rs.getInt("comment_count"), String.format(Locale.KOREA, "%.2f km", rs.getBigDecimal("total_distance")), rs.getObject("duration") == null ? null : String.valueOf(rs.getInt("duration")), rs.getObject("pace") == null ? null : String.valueOf(rs.getInt("pace")), rs.getBoolean("include_route_detail"), parts[2], decodeImages(rs.getString("image")));
	}

	private List<TipUploadResponse> tipQuery(String predicate, Long id) {
		Object[] args = id == null ? new Object[]{} : new Object[]{id};
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, COALESCE(cu.badge, 'NONE') AS badge,
			       cc.tip_type, cc.content_text, cc.include_route_detail, cc.image, cc.created_at,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='LIKE') AS like_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='COMMENT') AS comment_count,
			       FALSE AS liked, FALSE AS bookmarked, FALSE AS commented, FALSE AS mine
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
			SELECT cc.content_id, cc.author_user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, COALESCE(cu.badge, 'NONE') AS badge,
			       cc.tip_type, cc.content_text, cc.include_route_detail, cc.image, cc.created_at,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='LIKE') AS like_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='COMMENT') AS comment_count,
			       FALSE AS liked, FALSE AS bookmarked, FALSE AS commented, FALSE AS mine
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC LIMIT ? OFFSET ?", this::mapTip, args);
	}

	private List<TipUploadResponse> tipQueryForViewer(String predicate, Object[] args) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, COALESCE(cu.badge, 'NONE') AS badge,
			       cc.tip_type, cc.content_text, cc.include_route_detail, cc.image, cc.created_at,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='LIKE') AS like_count,
			       (SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.interaction_type='COMMENT') AS comment_count,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='LIKE') AS liked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='BOOKMARK') AS bookmarked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='COMMENT') AS commented,
			       (cc.author_user_id = ?) AS mine
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC", this::mapTip, args);
	}

	private TipUploadResponse mapTip(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		String[] parts = decodeTipContent(rs.getString("content_text"));
		String badge = rs.getString("badge");
		long writerId = rs.getLong("author_user_id");
		boolean mine = rs.getBoolean("mine");
		return new TipUploadResponse(rs.getLong("content_id"), writerId, rs.getString("nickname"), rs.getString("profile_image_url"), badgeOwned(badge), badge, fromTipType(rs.getString("tip_type")), parts[0], parts[1], rs.getBoolean("include_route_detail"), parts[2], decodeImages(rs.getString("image")), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getBoolean("liked"), rs.getBoolean("bookmarked"), rs.getBoolean("commented"), mine, rs.getTimestamp("created_at").toLocalDateTime().format(ISO));
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

	private CommentResponse findComment(long viewerUserId, long commentId) {
		return jdbcTemplate.query("""
			SELECT ci.interaction_id, ci.user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, ci.comment_text, ci.created_at,
			       COALESCE(cu.badge, 'NONE') AS badge
			FROM community_interactions ci JOIN users u ON u.user_id=ci.user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE ci.interaction_id=? AND ci.interaction_type='COMMENT'
			""", (rs, n) -> mapComment(rs, viewerUserId), commentId).stream().findFirst().orElse(null);
	}

	private List<CommentResponse> commentsForContent(long viewerUserId, long contentId) {
		return jdbcTemplate.query("""
			SELECT ci.interaction_id, ci.user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url, ci.comment_text, ci.created_at,
			       COALESCE(cu.badge, 'NONE') AS badge
			FROM community_interactions ci JOIN users u ON u.user_id=ci.user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE ci.content_id=? AND ci.interaction_type='COMMENT'
			ORDER BY ci.created_at ASC, ci.interaction_id ASC
			""", (rs, n) -> mapComment(rs, viewerUserId), contentId);
	}

	private CommentResponse mapComment(java.sql.ResultSet rs, long viewerUserId) throws java.sql.SQLException {
		String badge = rs.getString("badge");
		long writerId = rs.getLong("user_id");
		return new CommentResponse(rs.getLong("interaction_id"), writerId, rs.getString("nickname"), rs.getString("profile_image_url"), rs.getString("comment_text"), rs.getTimestamp("created_at").toLocalDateTime().format(ISO), badgeOwned(badge), badge, writerId == viewerUserId);
	}

	private static String toRelationshipStatus(String status) { return switch ((status == null ? "friends" : status).toLowerCase(Locale.ROOT)) { case "sent", "received" -> "REQUESTED"; case "blocked" -> "BLOCKED"; default -> "ACCEPTED"; }; }
	private static String feedPredicate(String predicate) { return "cc.content_type = 'POST' AND (" + predicate + ")"; }
	private static boolean blank(String value) { return value == null || value.isBlank(); }
	private static String like(String value) { return "%" + value.trim().toLowerCase(Locale.ROOT) + "%"; }
	private static int offset(int page, int size) { return page * size; }
	private static Object[] pageArgs(int page, int size) { return new Object[]{size, offset(page, size)}; }
	private static String normalizeSearchCategory(String category) { String value = category == null ? "ALL" : category.trim().toUpperCase(Locale.ROOT); return switch (value) { case "", "ALL" -> null; case "FREE", "자유", "ETC" -> "ETC"; case "TRAINING", "훈련" -> "TRAINING"; case "COURSE", "코스" -> "COURSE"; case "GEAR", "장비" -> "GEAR"; default -> value; }; }
	private static String normalizeScope(String privacy) { return switch ((privacy == null ? "PUBLIC" : privacy).toUpperCase(Locale.ROOT)) { case "FRIENDS" -> "FRIENDS"; case "PRIVATE" -> "PRIVATE"; default -> "PUBLIC"; }; }
	private static String normalizeTipType(String category) { return switch ((category == null ? "FREE" : category).trim().toUpperCase(Locale.ROOT)) { case "TRAINING", "훈련" -> "TRAINING"; case "COURSE", "코스" -> "COURSE"; case "GEAR", "장비" -> "GEAR"; case "FREE", "자유", "ETC", "" -> "ETC"; default -> "ETC"; }; }
	private static String fromTipType(String tipType) { return "ETC".equalsIgnoreCase(tipType) || tipType == null ? "FREE" : tipType; }
	private static boolean badgeOwned(String badge) { return badge != null && !"NONE".equalsIgnoreCase(badge); }
	private static CommentResponse commentResponse(Long commentId, long userId, String content) {
		return new CommentResponse(commentId, userId, null, null, content, java.time.LocalDateTime.now().format(ISO), false, "NONE", true);
	}
	private static String encodeFeedContent(String title, String content, String routeMapImageUrl) { return (title == null ? "" : title) + "\n---NEOSTRIDE-FEED---\n" + (content == null ? "" : content) + "\n---NEOSTRIDE-ROUTE---\n" + (routeMapImageUrl == null ? "" : routeMapImageUrl); }
	private static String[] decodeFeedContent(String raw) { String[] first = (raw == null ? "" : raw).split("\\n---NEOSTRIDE-FEED---\\n", 2); if (first.length == 1) return new String[]{null, first[0], null}; String rest = first[1]; String[] second = rest.split("\\n---NEOSTRIDE-ROUTE---\\n", 2); return new String[]{first[0], second.length > 0 ? second[0] : rest, second.length > 1 && !second[1].isBlank() ? second[1] : null}; }
	private static String encodeTipContent(String title, String content, String routeMapImageUrl) { return (title == null ? "" : title) + "\n---NEOSTRIDE-TIP---\n" + (content == null ? "" : content) + "\n---NEOSTRIDE-ROUTE---\n" + (routeMapImageUrl == null ? "" : routeMapImageUrl); }
	private static String[] decodeTipContent(String raw) { String[] first = (raw == null ? "" : raw).split("\\n---NEOSTRIDE-TIP---\\n", 2); String title = first.length > 0 ? first[0] : ""; String rest = first.length > 1 ? first[1] : ""; String[] second = rest.split("\\n---NEOSTRIDE-ROUTE---\\n", 2); return new String[]{title, second.length > 0 ? second[0] : rest, second.length > 1 && !second[1].isBlank() ? second[1] : null}; }
	private static String encodeImages(List<String> images) {
		if (images == null || images.isEmpty()) return null;
		List<String> normalized = images.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
		return normalized.isEmpty() ? null : String.join(IMAGE_DELIMITER, normalized);
	}
	private static List<String> decodeImages(String raw) {
		if (raw == null || raw.isBlank()) return List.of();
		return java.util.Arrays.stream(raw.split(java.util.regex.Pattern.quote(IMAGE_DELIMITER)))
				.filter(value -> value != null && !value.isBlank())
				.map(String::trim)
				.toList();
	}
	private static Integer nullableInt(Object value) { return value == null ? null : ((Number) value).intValue(); }
	private static Long nullableLong(Object value) { return value == null ? null : ((Number) value).longValue(); }
	private static BigDecimal nullToZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
	private static long generatedKey(KeyHolder keyHolder, String message) { Number key = keyHolder.getKey(); if (key == null) throw new IllegalStateException(message); return key.longValue(); }
}
