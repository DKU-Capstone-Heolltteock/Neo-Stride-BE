package com.neostride.server.community.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityRepositoryTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final CommunityRepository repository = new CommunityRepository(jdbcTemplate);

	@Test
	void listFeeds_filtersOutTipRows() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.listFeeds();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(sql.getValue()).contains("cc.content_type = 'POST'");
		assertThat(sql.getValue()).contains("cc.feed_scope <> 'PRIVATE'");
	}

	@Test
	void myFeeds_filtersOutTipsFromFeedTabs() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.myFeeds(1L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(sql.getValue()).contains("cc.content_type = 'POST'");
		assertThat(sql.getValue()).contains("cc.author_user_id = ?");
	}

	@Test
	void createComment_persistsCommentTextInsteadOfOnlyCountingInteraction() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(99L))).thenReturn(List.of(commentRow(99L, 1L, "좋은 팁입니다")));
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 99L));
			return 1;
		}).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

		repository.createComment(1L, 10L, new com.neostride.server.community.dto.CommentRequest("좋은 팁입니다"));

		ArgumentCaptor<PreparedStatementCreator> creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
		verify(jdbcTemplate).update(creator.capture(), any(KeyHolder.class));
		Connection connection = mock(Connection.class);
		PreparedStatement preparedStatement = mock(PreparedStatement.class);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		when(connection.prepareStatement(sql.capture(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(preparedStatement);

		creator.getValue().createPreparedStatement(connection);

		assertThat(sql.getValue()).contains("comment_text");
		verify(preparedStatement).setString(3, "좋은 팁입니다");
	}

	@Test
	void listTipsWithViewer_projectsOwnershipAndCommentMetadataForTipTab() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.listTips(1L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(sql.getValue()).contains("cc.author_user_id");
		assertThat(sql.getValue()).contains("liked");
		assertThat(sql.getValue()).contains("bookmarked");
		assertThat(sql.getValue()).contains("commented");
	}

	@Test
	void listTips_mapsLegacyEtcTipTypeToFreeCategoryForClient() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<com.neostride.server.community.dto.TipUploadResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(tipRow("ETC"), 0));
		});

		var tips = repository.listTips();

		assertThat(tips).hasSize(1);
		assertThat(tips.getFirst().category()).isEqualTo("FREE");
	}

	private ResultSet tipRow(String tipType) throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("content_id")).thenReturn(7L);
		when(rs.getLong("author_user_id")).thenReturn(1L);
		when(rs.getString("nickname")).thenReturn("neo");
		when(rs.getString("profile_image_url")).thenReturn("photo.png");
		when(rs.getString("badge")).thenReturn("NONE");
		when(rs.getString("tip_type")).thenReturn(tipType);
		when(rs.getString("content_text")).thenReturn("title\n---NEOSTRIDE-TIP---\nbody\n---NEOSTRIDE-ROUTE---\n");
		when(rs.getBoolean("include_route_detail")).thenReturn(false);
		when(rs.getString("image")).thenReturn(null);
		when(rs.getInt("like_count")).thenReturn(0);
		when(rs.getInt("comment_count")).thenReturn(0);
		when(rs.getBoolean("liked")).thenReturn(false);
		when(rs.getBoolean("bookmarked")).thenReturn(false);
		when(rs.getBoolean("commented")).thenReturn(false);
		when(rs.getBoolean("mine")).thenReturn(false);
		when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf("2026-05-11 00:00:00"));
		return rs;
	}

	private com.neostride.server.community.dto.CommentResponse commentRow(long commentId, long writerId, String content) {
		return new com.neostride.server.community.dto.CommentResponse(commentId, writerId, "neo", null, content, "2026-05-11T00:00:00", false, "NONE", true);
	}
}
