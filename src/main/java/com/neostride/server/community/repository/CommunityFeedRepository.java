package com.neostride.server.community.repository;

import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedDetailResponse;
import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.platform.event.NotificationRequestedEvent;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

final class CommunityFeedRepository {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
	private static final String PUBLIC_FEED_PREDICATE = "cc.content_type = 'POST' AND cc.feed_scope = 'PUBLIC'";
	private static final String VIEWER_SCOPE_PREDICATE = "(cc.feed_scope = 'PUBLIC' OR cc.author_user_id = ? OR (cc.feed_scope = 'FRIENDS' AND EXISTS (SELECT 1 FROM relationships r WHERE r.status = 'ACCEPTED' AND ((r.user1_id = ? AND r.user2_id = cc.author_user_id) OR (r.user2_id = ? AND r.user1_id = cc.author_user_id)))))";
	private static final String VIEWER_FEED_PREDICATE = "cc.content_type = 'POST' AND " + VIEWER_SCOPE_PREDICATE;

	private final JdbcTemplate jdbcTemplate;
	private final ApplicationEventPublisher eventPublisher;
	private final CommunityInteractionRepository interactionRepository;

	CommunityFeedRepository(
			JdbcTemplate jdbcTemplate,
			ApplicationEventPublisher eventPublisher,
			CommunityInteractionRepository interactionRepository
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.eventPublisher = eventPublisher;
		this.interactionRepository = interactionRepository;
	}

	List<CommunityContentResponse> myFeeds(long userId) {
		return contentQuery("cc.author_user_id = ?", userId, userId);
	}

	List<CommunityContentResponse> publicFeedsByUser(long userId) {
		return contentQuery("cc.author_user_id = ? AND cc.feed_scope = 'PUBLIC'", 0L, userId);
	}

	List<CommunityContentResponse> feedsByUserForViewer(Long viewerUserId, long userId) {
		if (viewerUserId == null) return publicFeedsByUser(userId);
		return contentQuery("cc.author_user_id = ? AND " + VIEWER_SCOPE_PREDICATE, viewerUserId, userId, viewerUserId, viewerUserId, viewerUserId);
	}

	List<CommunityContentResponse> taggedFeeds(long userId) {
		return contentQuery("EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id = cc.content_id AND ci.interaction_type='TAG' AND ci.tagged_user_id = ?)", userId, userId);
	}

	List<CommunityContentResponse> interactedFeeds(long userId, String type) {
		return contentQuery("EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id = cc.content_id AND ci.interaction_type='" + type + "' AND ci.user_id = ?)", userId, userId);
	}

	long insertFeed(long userId, FeedUploadRequest request) {
		Long runningRecordId = ownedRunningRecordId(userId, request.runningRecordId());
		KeyHolder kh = new GeneratedKeyHolder();
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement("""
				INSERT INTO community_contents (author_user_id, running_record_id, include_route_detail, content_type, feed_scope, content_text, image, title, body_text, route_map_image_url, distance_km, running_time_text, pace_text)
				VALUES (?, ?, ?, 'POST', ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, userId);
			if (runningRecordId == null) {
				ps.setObject(2, null);
			} else {
				ps.setLong(2, runningRecordId);
			}
			ps.setBoolean(3, request.mapVisible());
			ps.setString(4, normalizeScope(request.privacy()));
			ps.setString(5, CommunityContentCodec.encodeFeedContent(request.title(), request.content(), request.routeMapImageUri(), request.distance(), request.runningTime(), request.pace()));
			ps.setString(6, CommunityContentCodec.encodeImages(request.imageUrls()));
			ps.setString(7, request.title());
			ps.setString(8, request.content());
			ps.setString(9, request.routeMapImageUri());
			ps.setBigDecimal(10, request.distance());
			ps.setString(11, request.runningTime());
			ps.setString(12, request.pace());
			return ps;
		}, kh);
		long contentId = generatedKey(kh, "피드 ID를 생성하지 못했습니다.");
		insertContentImages(contentId, request.imageUrls());
		if (request.taggedUserIds() != null) {
			for (Long tagged : new java.util.LinkedHashSet<>(request.taggedUserIds())) {
				if (tagged != null) {
					jdbcTemplate.update("INSERT INTO community_interactions (user_id, content_id, interaction_type, tagged_user_id) VALUES (?, ?, 'TAG', ?)", userId, contentId, tagged);
					notifyTaggedUser(userId, contentId, tagged);
				}
			}
		}
		return contentId;
	}

	FeedUploadResponse findFeed(long feedId) {
		return feedQuery("cc.content_id = ?", feedId).stream().findFirst().orElse(null);
	}

	List<FeedUploadResponse> listFeeds() {
		return listFeeds(null);
	}

	List<FeedUploadResponse> listFeeds(Long viewerUserId) {
		if (viewerUserId == null) return feedQuery(PUBLIC_FEED_PREDICATE);
		return feedQueryForViewer(VIEWER_FEED_PREDICATE + " AND " + blockedByCurrentUserPredicate(), viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId, viewerUserId);
	}

	List<FeedUploadResponse> listFeedsPage(Long viewerUserId, LocalDateTime cursorCreatedAt, Long cursorId, int limit) {
		String predicate = viewerUserId == null ? PUBLIC_FEED_PREDICATE : VIEWER_FEED_PREDICATE;
		java.util.List<Object> predicateArgs = new java.util.ArrayList<>();
		if (viewerUserId != null) {
			predicateArgs.add(viewerUserId);
			predicateArgs.add(viewerUserId);
			predicateArgs.add(viewerUserId);
		}
		if (cursorCreatedAt != null && cursorId != null) {
			Timestamp cursorTimestamp = Timestamp.valueOf(cursorCreatedAt);
			predicate += " AND (cc.created_at < ? OR (cc.created_at = ? AND cc.content_id < ?))";
			predicateArgs.add(cursorTimestamp);
			predicateArgs.add(cursorTimestamp);
			predicateArgs.add(cursorId);
		}
		java.util.List<Object> limitedArgs = new java.util.ArrayList<>(predicateArgs);
		limitedArgs.add(limit);
		if (viewerUserId == null) {
			return feedQueryLimited(predicate, limitedArgs.toArray());
		}
		java.util.List<Object> args = new java.util.ArrayList<>();
		args.add(viewerUserId);
		args.add(viewerUserId);
		args.add(viewerUserId);
		args.add(viewerUserId);
		args.add(viewerUserId);
		args.addAll(predicateArgs);
		args.add(viewerUserId);
		args.add(viewerUserId);
		args.add(limit);
		return feedQueryForViewerLimited(predicate + " AND " + blockedByCurrentUserPredicate(), args.toArray());
	}

	FeedDetailResponse findFeedDetail(long userId, long feedId, Integer commentLimit) {
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id, COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.badge, 'NONE') AS badge, cc.created_at, cc.content_text, cc.title, cc.body_text, cc.route_map_image_url, cc.course_address, cc.distance_km, cc.running_time_text, cc.pace_text, cc.include_route_detail, COALESCE((SELECT GROUP_CONCAT(cci.image_url ORDER BY cci.image_order SEPARATOR '\n---NEOSTRIDE-IMAGE---\n') FROM community_content_images cci WHERE cci.content_id = cc.content_id), cc.image) AS image_urls,
			       rr.run_record_id AS joined_running_record_id, COALESCE(rr.total_distance, 0) AS total_distance, rr.duration, rr.pace,
			       COALESCE(stats.tagged_count, 0) AS tagged_count,
			       COALESCE(stats.like_count, 0) AS like_count,
			       COALESCE(stats.comment_count, 0) AS comment_count,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='LIKE') AS liked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='BOOKMARK') AS bookmarked
			FROM community_contents cc JOIN users u ON u.user_id=cc.author_user_id LEFT JOIN community_users cu ON cu.user_id=u.user_id
			LEFT JOIN community_content_stats stats ON stats.content_id=cc.content_id
			LEFT JOIN running_records rr ON rr.run_record_id=cc.running_record_id WHERE cc.content_type='POST' AND cc.content_id=? AND
			""" + VIEWER_SCOPE_PREDICATE + " AND " + blockedByCurrentUserPredicate() + """
			""", (rs, n) -> {
			DecodedFeedContent parts = CommunityContentCodec.decodeFeedContent(rs);
			String badge = rs.getString("badge");
			long contentId = rs.getLong("content_id");
			return new FeedDetailResponse(contentId, rs.getLong("author_user_id"), rs.getString("profile_image_url"), rs.getString("nickname"), badgeOwned(badge), badge, rs.getTimestamp("created_at").toLocalDateTime().format(ISO), parts.title(), parts.content(), rs.getInt("tagged_count"), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getBoolean("liked"), rs.getBoolean("bookmarked"), rs.getLong("author_user_id") == userId, CommunityContentCodec.feedDistance(rs, parts), CommunityContentCodec.feedDuration(rs, parts), CommunityContentCodec.feedPace(rs, parts), rs.getBoolean("include_route_detail"), parts.routeMapImageUri(), CommunityContentCodec.decodeImages(CommunityContentCodec.imageUrls(rs)), interactionRepository.commentsForContent(userId, contentId, null, null, normalizedLimit(commentLimit)));
		}, userId, userId, feedId, userId, userId, userId, userId, userId).stream().findFirst().orElse(null);
	}

	void updateFeed(long userId, long feedId, FeedUploadRequest request) {
		String contentText = CommunityContentCodec.encodeFeedContent(request.title(), request.content(), request.routeMapImageUri(), request.distance(), request.runningTime(), request.pace());
		int updated;
		if (request.runningRecordId() != null) {
			updated = jdbcTemplate.update("UPDATE community_contents SET running_record_id=?, include_route_detail=?, feed_scope=?, content_text=?, image=?, title=?, body_text=?, route_map_image_url=?, distance_km=?, running_time_text=?, pace_text=? WHERE content_id=? AND author_user_id=? AND content_type='POST'", ownedRunningRecordId(userId, request.runningRecordId()), request.mapVisible(), normalizeScope(request.privacy()), contentText, CommunityContentCodec.encodeImages(request.imageUrls()), request.title(), request.content(), request.routeMapImageUri(), request.distance(), request.runningTime(), request.pace(), feedId, userId);
		} else {
			updated = jdbcTemplate.update("UPDATE community_contents SET include_route_detail=?, feed_scope=?, content_text=?, image=?, title=?, body_text=?, route_map_image_url=?, distance_km=?, running_time_text=?, pace_text=? WHERE content_id=? AND author_user_id=? AND content_type='POST'", request.mapVisible(), normalizeScope(request.privacy()), contentText, CommunityContentCodec.encodeImages(request.imageUrls()), request.title(), request.content(), request.routeMapImageUri(), request.distance(), request.runningTime(), request.pace(), feedId, userId);
		}
		if (updated > 0) {
			replaceContentImages(feedId, request.imageUrls());
		}
	}

	List<FriendResponse> getTaggedUsers(Long viewerUserId, long feedId) {
		String visibilityPredicate = viewerUserId == null ? PUBLIC_FEED_PREDICATE : VIEWER_FEED_PREDICATE + " AND " + blockedByCurrentUserPredicate();
		java.util.List<Object> args = new java.util.ArrayList<>();
		args.add(feedId);
		if (viewerUserId != null) {
			args.add(viewerUserId);
			args.add(viewerUserId);
			args.add(viewerUserId);
			args.add(viewerUserId);
			args.add(viewerUserId);
		}
		return jdbcTemplate.query("""
			SELECT u.user_id, COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.badge, 'NONE') AS badge_tier,
			       (SELECT COUNT(*) FROM relationships rf WHERE (rf.user1_id = u.user_id OR rf.user2_id = u.user_id) AND rf.status='ACCEPTED') AS friend_count,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url
			FROM community_interactions ci
			JOIN community_contents cc ON cc.content_id = ci.content_id
			JOIN users u ON u.user_id=ci.tagged_user_id
			LEFT JOIN community_users cu ON cu.user_id=u.user_id
			WHERE ci.content_id=? AND ci.interaction_type='TAG' AND
			""" + visibilityPredicate + " ORDER BY u.user_id", (rs, n) -> new FriendResponse(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("badge_tier"), rs.getInt("friend_count"), rs.getString("profile_image_url"), "tagged"), args.toArray());
	}

	private List<CommunityContentResponse> contentQuery(String predicate, long viewerUserId, Object... predicateArgs) {
		java.util.List<Object> args = new java.util.ArrayList<>();
		args.add(viewerUserId);
		args.add(viewerUserId);
		args.add(viewerUserId);
		args.add(viewerUserId);
		args.addAll(java.util.Arrays.asList(predicateArgs));
		args.add(viewerUserId);
		args.add(viewerUserId);
		return jdbcTemplate.query("""
			SELECT cc.content_id, cc.author_user_id AS user_id,
			       COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       COALESCE(cu.badge, 'NONE') AS badge_tier,
			       cc.content_text, cc.title, cc.body_text, cc.route_map_image_url, cc.course_address, cc.distance_km, cc.running_time_text, cc.pace_text, cc.include_route_detail, COALESCE((SELECT GROUP_CONCAT(cci.image_url ORDER BY cci.image_order SEPARATOR '\n---NEOSTRIDE-IMAGE---\n') FROM community_content_images cci WHERE cci.content_id = cc.content_id), cc.image) AS image_urls,
			       rr.run_record_id AS joined_running_record_id, COALESCE(rr.total_distance, 0) AS total_distance, rr.duration, rr.pace, cc.created_at,
			       COALESCE(stats.tagged_count, 0) AS tagged_count,
			       COALESCE(stats.like_count, 0) AS like_count,
			       COALESCE(stats.comment_count, 0) AS comment_count,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='LIKE') AS liked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='BOOKMARK') AS bookmarked,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.user_id=? AND ci.interaction_type='COMMENT') AS commented,
			       EXISTS (SELECT 1 FROM community_interactions ci WHERE ci.content_id=cc.content_id AND ci.tagged_user_id=? AND ci.interaction_type='TAG') AS tagged
			FROM community_contents cc
			JOIN users u ON u.user_id = cc.author_user_id
			LEFT JOIN community_users cu ON cu.user_id = u.user_id
			LEFT JOIN running_records rr ON rr.run_record_id = cc.running_record_id
			LEFT JOIN community_content_stats stats ON stats.content_id = cc.content_id
			WHERE
			""" + feedPredicate(predicate) + " AND " + blockedByCurrentUserPredicate() + " ORDER BY cc.created_at DESC, cc.content_id DESC", (rs, n) -> {
			DecodedFeedContent decoded = CommunityContentCodec.decodeFeedContent(rs);
			return new CommunityContentResponse(
					rs.getLong("content_id"),
					rs.getLong("user_id"),
					rs.getString("nickname"),
					decoded.title(),
					decoded.content(),
					CommunityContentCodec.communityDistance(rs, decoded),
					CommunityContentCodec.communityDuration(rs, decoded),
					CommunityContentCodec.communityPace(rs, decoded),
					rs.getTimestamp("created_at").toLocalDateTime().format(ISO),
					rs.getString("profile_image_url"),
					CommunityContentCodec.decodeImages(CommunityContentCodec.imageUrls(rs)),
					rs.getInt("like_count"),
					rs.getInt("comment_count"),
					rs.getInt("tagged_count"),
					rs.getBoolean("liked"),
					rs.getBoolean("bookmarked"),
					rs.getBoolean("commented"),
					rs.getBoolean("tagged"),
					rs.getString("badge_tier"),
					decoded.routeMapImageUri()
			);
		}, args.toArray());
	}

	private List<FeedUploadResponse> feedQuery(String predicate, Object... args) {
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
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC", (rs, n) -> mapFeed(rs), args);
	}

	private List<FeedUploadResponse> feedQueryForViewer(String predicate, Object... args) {
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
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC", (rs, n) -> mapFeed(rs), args);
	}

	private List<FeedUploadResponse> feedQueryLimited(String predicate, Object... args) {
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
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC LIMIT ?", (rs, n) -> mapFeed(rs), args);
	}

	private List<FeedUploadResponse> feedQueryForViewerLimited(String predicate, Object... args) {
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
			""" + predicate + " ORDER BY cc.created_at DESC, cc.content_id DESC LIMIT ?", (rs, n) -> mapFeed(rs), args);
	}

	private FeedUploadResponse mapFeed(ResultSet rs) throws SQLException {
		DecodedFeedContent parts = CommunityContentCodec.decodeFeedContent(rs);
		long writerId = rs.getLong("author_user_id");
		String badge = rs.getString("badge");
		return new FeedUploadResponse(rs.getLong("content_id"), rs.getString("profile_image_url"), rs.getString("nickname"), badgeOwned(badge), badge, rs.getTimestamp("created_at").toLocalDateTime().format(ISO), parts.title(), parts.content(), rs.getInt("tagged_count"), rs.getInt("like_count"), rs.getInt("comment_count"), CommunityContentCodec.feedDistance(rs, parts), CommunityContentCodec.feedDuration(rs, parts), CommunityContentCodec.feedPace(rs, parts), rs.getBoolean("include_route_detail"), parts.routeMapImageUri(), CommunityContentCodec.decodeImages(CommunityContentCodec.imageUrls(rs)), rs.getBoolean("liked"), rs.getBoolean("bookmarked"), rs.getBoolean("commented"), rs.getBoolean("tagged"), rs.getBoolean("mine"), writerId);
	}

	private void notifyTaggedUser(long senderUserId, long contentId, long taggedUserId) {
		if (senderUserId == taggedUserId) return;
		eventPublisher.publishEvent(new NotificationRequestedEvent(taggedUserId, "FEED_TAG", notificationActorName(senderUserId) + "님이 회원님을 피드에 태그했습니다.", "/api/community/feeds/" + contentId));
	}

	private String notificationActorName(long userId) {
		List<String> rows = jdbcTemplate.query("""
			SELECT COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id
			WHERE u.user_id = ?
			""", (rs, n) -> rs.getString("nickname"), userId);
		return rows == null || rows.isEmpty() || rows.getFirst() == null || rows.getFirst().isBlank() ? "러너" : rows.getFirst();
	}

	private Long ownedRunningRecordId(long userId, Long runningRecordId) {
		if (runningRecordId == null) return null;
		if (runningRecordId <= 0) throw new IllegalArgumentException("running_record_id가 올바르지 않습니다.");
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM running_records WHERE run_record_id=? AND user_id=?", Integer.class, runningRecordId, userId);
		if (count == null || count == 0) throw new IllegalArgumentException("running_record_id가 올바르지 않습니다.");
		return runningRecordId;
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

	private static String feedPredicate(String predicate) {
		return "cc.content_type = 'POST' AND (" + predicate + ")";
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

	private static String normalizeScope(String privacy) {
		return switch ((privacy == null ? "PUBLIC" : privacy).toUpperCase(Locale.ROOT)) {
			case "FRIENDS" -> "FRIENDS";
			case "PRIVATE" -> "PRIVATE";
			default -> "PUBLIC";
		};
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
