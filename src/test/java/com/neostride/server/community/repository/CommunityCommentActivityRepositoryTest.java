package com.neostride.server.community.repository;

import com.neostride.server.community.dto.MyCommentActivityResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityCommentActivityRepositoryTest {
	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final CommunityCommentActivityRepository repository = new CommunityCommentActivityRepository(jdbcTemplate);

	@Test
	void myCommentActivities_returnsBoundedCommentRowsWithEditableCommentIdentifiers() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<MyCommentActivityResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(feedCommentActivityRow(), 0), mapper.mapRow(tipCommentActivityRow(), 1));
		});

		LocalDateTime cursorCreatedAt = LocalDateTime.parse("2026-06-02T11:00:00");
		List<MyCommentActivityResponse> rows = repository.myCommentActivities(1L, cursorCreatedAt, 98L, 21);

		assertThat(rows).hasSize(2);
		MyCommentActivityResponse feed = rows.get(0);
		assertThat(feed.contentType()).isEqualTo("FEED");
		assertThat(feed.contentId()).isEqualTo(10L);
		assertThat(feed.commentId()).isEqualTo(99L);
		assertThat(feed.commentText()).isEqualTo("feed comment");
		assertThat(feed.commentMine()).isTrue();
		assertThat(feed.contentMine()).isFalse();
		assertThat(feed.totalDistance()).isEqualByComparingTo("5.25");
		assertThat(feed.duration()).isEqualTo(1800);
		assertThat(feed.pace()).isEqualTo(343);
		assertThat(feed.imageUrls()).containsExactly("/uploads/community/feed.jpg");

		MyCommentActivityResponse tip = rows.get(1);
		assertThat(tip.contentType()).isEqualTo("TIP");
		assertThat(tip.category()).isEqualTo("FREE");
		assertThat(tip.commentId()).isEqualTo(100L);
		assertThat(tip.routeMapUrl()).isEqualTo("/uploads/routes/tip.png");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue())
				.contains("ci.interaction_id AS comment_id")
				.contains("ci.user_id=? AND ci.interaction_type='COMMENT'")
				.contains("AND (ci.created_at < ? OR (ci.created_at = ? AND ci.interaction_id < ?))")
				.contains("ORDER BY ci.created_at DESC, ci.interaction_id DESC")
				.contains("LIMIT ?")
				.doesNotContain("GROUP BY");
		Timestamp cursorTimestamp = Timestamp.valueOf(cursorCreatedAt);
		assertThat(args.getValue()).containsExactly(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L,
				cursorTimestamp, cursorTimestamp, 98L, 21);
	}

	@Test
	void myCommentActivities_hidesFeedRouteMapUrlWhenRouteDetailIsPrivate() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<MyCommentActivityResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(privateFeedCommentActivityRow(), 0));
		});

		MyCommentActivityResponse feed = repository.myCommentActivities(1L, null, null, 21).getFirst();

		assertThat(feed.gpsVisible()).isFalse();
		assertThat(feed.routeMapUrl()).isNull();
	}

	@Test
	void myCommentActivities_omitsCursorPredicateWhenCursorIsAbsentButKeepsLimit() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.myCommentActivities(1L, null, null, 21);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue())
				.doesNotContain("ci.created_at < ?")
				.contains("LIMIT ?");
		assertThat(args.getValue()).containsExactly(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 21);
	}

	private ResultSet feedCommentActivityRow() throws Exception {
		ResultSet rs = baseRow("POST", 10L, 2L, 99L, "feed comment");
		when(rs.getString("title")).thenReturn("feed title");
		when(rs.getString("body_text")).thenReturn("feed body");
		when(rs.getString("route_map_image_url")).thenReturn("/uploads/routes/feed.png");
		when(rs.getBigDecimal("distance_km")).thenReturn(new BigDecimal("5.25"));
		when(rs.getString("running_time_text")).thenReturn("30:00");
		when(rs.getString("pace_text")).thenReturn("5:43/km");
		when(rs.getObject("joined_running_record_id")).thenReturn(null);
		when(rs.getString("image_urls")).thenReturn("/uploads/community/feed.jpg");
		when(rs.getBoolean("include_route_detail")).thenReturn(true);
		when(rs.getInt("tagged_count")).thenReturn(1);
		when(rs.getBoolean("tagged")).thenReturn(true);
		return rs;
	}

	private ResultSet privateFeedCommentActivityRow() throws Exception {
		ResultSet rs = feedCommentActivityRow();
		when(rs.getBoolean("include_route_detail")).thenReturn(false);
		return rs;
	}

	private ResultSet tipCommentActivityRow() throws Exception {
		ResultSet rs = baseRow("TIP", 7L, 3L, 100L, "tip comment");
		when(rs.getString("tip_type")).thenReturn("ETC");
		when(rs.getString("title")).thenReturn("tip title");
		when(rs.getString("body_text")).thenReturn("tip body");
		when(rs.getString("route_map_image_url")).thenReturn("/uploads/routes/tip.png");
		when(rs.getString("image_urls")).thenReturn("/uploads/community/tip.jpg");
		when(rs.getBoolean("include_route_detail")).thenReturn(false);
		return rs;
	}

	private ResultSet baseRow(String contentType, long contentId, long writerId, long commentId, String commentText) throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString("content_type")).thenReturn(contentType);
		when(rs.getLong("content_id")).thenReturn(contentId);
		when(rs.getLong("author_user_id")).thenReturn(writerId);
		when(rs.getString("nickname")).thenReturn("runner");
		when(rs.getString("profile_image_url")).thenReturn("/uploads/profile/runner.jpg");
		when(rs.getString("badge")).thenReturn("NONE");
		when(rs.getTimestamp("content_created_at")).thenReturn(Timestamp.valueOf("2026-06-02 10:00:00"));
		when(rs.getLong("comment_id")).thenReturn(commentId);
		when(rs.getString("comment_text")).thenReturn(commentText);
		when(rs.getTimestamp("comment_created_at")).thenReturn(Timestamp.valueOf("2026-06-02 10:05:00"));
		when(rs.getInt("like_count")).thenReturn(3);
		when(rs.getInt("comment_count")).thenReturn(2);
		when(rs.getBoolean("liked")).thenReturn(false);
		when(rs.getBoolean("bookmarked")).thenReturn(true);
		when(rs.getBoolean("commented")).thenReturn(true);
		return rs;
	}
}
