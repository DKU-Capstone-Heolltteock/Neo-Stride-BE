package com.neostride.server.community.repository;

import com.neostride.server.community.dto.MyCommentActivityResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

final class CommunityCommentActivityRepository {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
	private static final String VIEWER_SCOPE_PREDICATE = "(cc.feed_scope = 'PUBLIC' OR cc.author_user_id = ? OR (cc.feed_scope = 'FRIENDS' AND EXISTS (SELECT 1 FROM relationships r WHERE r.status = 'ACCEPTED' AND ((r.user1_id = ? AND r.user2_id = cc.author_user_id) OR (r.user2_id = ? AND r.user1_id = cc.author_user_id)))))";

	private final JdbcTemplate jdbcTemplate;

	CommunityCommentActivityRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<MyCommentActivityResponse> myCommentActivities(long userId, LocalDateTime cursorCreatedAt, Long cursorId, int limit) {
		String cursorPredicate = "";
		List<Object> args = new ArrayList<>();
		args.add(userId);
		args.add(userId);
		args.add(userId);
		args.add(userId);
		args.add(userId);
		args.add(userId);
		args.add(userId);
		args.add(userId);
		args.add(userId);
		if (cursorCreatedAt != null && cursorId != null) {
			cursorPredicate = " AND (ci.created_at < ? OR (ci.created_at = ? AND ci.interaction_id < ?))";
			Timestamp cursorTimestamp = Timestamp.valueOf(cursorCreatedAt);
			args.add(cursorTimestamp);
			args.add(cursorTimestamp);
			args.add(cursorId);
		}
		args.add(limit);
		return jdbcTemplate.query("""
			SELECT ci.interaction_id AS comment_id, ci.comment_text, ci.created_at AS comment_created_at,
			       cc.content_id, cc.content_type, cc.author_user_id,
			       COALESCE(cu.community_profile_name, u.community_profile_name, u.name) AS nickname,
			       COALESCE(cu.profile_photo, u.profile_photo) AS profile_image_url,
			       COALESCE(cu.badge, 'NONE') AS badge,
			       cc.tip_type, cc.content_text, cc.title, cc.body_text, cc.route_map_image_url,
			       cc.course_address, cc.distance_km, cc.running_time_text, cc.pace_text,
			       cc.include_route_detail,
			       COALESCE((SELECT GROUP_CONCAT(cci.image_url ORDER BY cci.image_order SEPARATOR '\n---NEOSTRIDE-IMAGE---\n')
			                 FROM community_content_images cci WHERE cci.content_id = cc.content_id), cc.image) AS image_urls,
			       rr.run_record_id AS joined_running_record_id,
			       COALESCE(rr.total_distance, 0) AS total_distance, rr.duration, rr.pace,
			       cc.created_at AS content_created_at,
			       COALESCE(stats.tagged_count, 0) AS tagged_count,
			       COALESCE(stats.like_count, 0) AS like_count,
			       COALESCE(stats.comment_count, 0) AS comment_count,
			       EXISTS (SELECT 1 FROM community_interactions li WHERE li.content_id=cc.content_id AND li.user_id=? AND li.interaction_type='LIKE') AS liked,
			       EXISTS (SELECT 1 FROM community_interactions bi WHERE bi.content_id=cc.content_id AND bi.user_id=? AND bi.interaction_type='BOOKMARK') AS bookmarked,
			       TRUE AS commented,
			       EXISTS (SELECT 1 FROM community_interactions ti WHERE ti.content_id=cc.content_id AND ti.tagged_user_id=? AND ti.interaction_type='TAG') AS tagged
			FROM community_interactions ci
			JOIN community_contents cc ON cc.content_id = ci.content_id
			JOIN users u ON u.user_id = cc.author_user_id
			LEFT JOIN community_users cu ON cu.user_id = u.user_id
			LEFT JOIN running_records rr ON rr.run_record_id = cc.running_record_id
			LEFT JOIN community_content_stats stats ON stats.content_id = cc.content_id
			WHERE ci.user_id=? AND ci.interaction_type='COMMENT'
			  AND (cc.content_type='TIP' OR (cc.content_type='POST' AND """ + VIEWER_SCOPE_PREDICATE + """
			  ))
			  AND """ + blockedByCurrentUserPredicate() + """
			""" + cursorPredicate + """
			ORDER BY ci.created_at DESC, ci.interaction_id DESC
			LIMIT ?
			""", (rs, n) -> mapRow(rs, userId), args.toArray());
	}

	private MyCommentActivityResponse mapRow(ResultSet rs, long userId) throws SQLException {
		String dbContentType = rs.getString("content_type");
		boolean tip = "TIP".equalsIgnoreCase(dbContentType);
		String contentType = tip ? "TIP" : "FEED";
		String title;
		String content;
		String category = null;
		String routeMapUrl;
		BigDecimal totalDistance = null;
		Integer duration = null;
		Integer pace = null;
		if (tip) {
			String[] parts = CommunityContentCodec.decodeTipContent(rs);
			title = parts[0];
			content = parts[1];
			routeMapUrl = parts[2];
			category = fromTipType(rs.getString("tip_type"));
		} else {
			DecodedFeedContent decoded = CommunityContentCodec.decodeFeedContent(rs);
			title = decoded.title();
			content = decoded.content();
			routeMapUrl = decoded.routeMapImageUri();
			totalDistance = CommunityContentCodec.communityDistance(rs, decoded);
			duration = CommunityContentCodec.communityDuration(rs, decoded);
			pace = CommunityContentCodec.communityPace(rs, decoded);
		}
		String badge = rs.getString("badge");
		long writerId = rs.getLong("author_user_id");
		return new MyCommentActivityResponse(
				contentType,
				rs.getLong("content_id"),
				writerId,
				rs.getString("nickname"),
				rs.getString("profile_image_url"),
				badgeOwned(badge),
				badge,
				category,
				title,
				content,
				iso(rs.getTimestamp("content_created_at")),
				totalDistance,
				duration,
				pace,
				rs.getBoolean("include_route_detail"),
				routeMapUrl,
				CommunityContentCodec.decodeImages(CommunityContentCodec.imageUrls(rs)),
				rs.getInt("like_count"),
				rs.getInt("comment_count"),
				rs.getInt("tagged_count"),
				rs.getBoolean("liked"),
				rs.getBoolean("bookmarked"),
				rs.getBoolean("commented"),
				rs.getBoolean("tagged"),
				writerId == userId,
				rs.getLong("comment_id"),
				rs.getString("comment_text"),
				iso(rs.getTimestamp("comment_created_at")),
				true
		);
	}

	private static String fromTipType(String tipType) {
		return "ETC".equalsIgnoreCase(tipType) || tipType == null ? "FREE" : tipType;
	}

	private static boolean badgeOwned(String badge) {
		return badge != null && !"NONE".equalsIgnoreCase(badge);
	}

	private static String iso(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toLocalDateTime().format(ISO);
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
