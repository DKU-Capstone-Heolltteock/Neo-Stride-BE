package com.neostride.server.community.repository;

import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
		assertThat(sql.getValue()).contains("cc.feed_scope IN ('PUBLIC', 'FRIENDS')");
	}


	@Test
	void listFeeds_usesContentStatsTableForCounts() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.listFeeds();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(sql.getValue()).contains("LEFT JOIN community_content_stats stats");
		assertThat(sql.getValue()).contains("community_content_images");
		assertThat(sql.getValue()).doesNotContain("SUM(interaction_type='LIKE')");
	}

	@Test
	void listFeedsPage_usesLimitWithoutChangingLegacyListContract() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.listFeedsPage(null, null, null, 21);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue()).contains("LIMIT ?");
		assertThat(args.getValue()).containsExactly(21);
	}

	@Test
	void listFeedsPageWithViewer_usesCursorBeforeBlockedPredicateArgs() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.listFeedsPage(1L, LocalDateTime.parse("2026-05-26T22:14:32"), 76L, 11);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue()).contains("cc.created_at < ?", "cc.content_id < ?", "LIMIT ?");
		assertThat(args.getValue()).containsExactly(
				1L, 1L, 1L, 1L, 1L,
				Timestamp.valueOf("2026-05-26 22:14:32"),
				Timestamp.valueOf("2026-05-26 22:14:32"),
				76L, 1L, 1L, 11
		);
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
	void insertTip_persistsCourseAddressInEncodedContent() throws Exception {
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 11L));
			return 1;
		}).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
		var request = new com.neostride.server.community.dto.TipUploadRequest("COURSE", "title", "body", true, "route.png", "Seoul Forest", List.of("tip.png"));

		repository.insertTip(1L, request);

		ArgumentCaptor<PreparedStatementCreator> creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
		verify(jdbcTemplate).update(creator.capture(), any(KeyHolder.class));
		Connection connection = mock(Connection.class);
		PreparedStatement preparedStatement = mock(PreparedStatement.class);
		when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(preparedStatement);

		creator.getValue().createPreparedStatement(connection);

		ArgumentCaptor<String> contentText = ArgumentCaptor.forClass(String.class);
		verify(preparedStatement).setString(eq(4), contentText.capture());
		assertThat(contentText.getValue()).contains("---NEOSTRIDE-ADDR---", "Seoul Forest");
	}

	@Test
	void insertFeed_persistsRunningMetricsForAndroidUploadFallback() throws Exception {
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 10L));
			return 1;
		}).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
		FeedUploadRequest request = new FeedUploadRequest("title", "body", "PUBLIC", true, "route.png", List.of(), List.of(), new BigDecimal("3.2"), "20:00", "6'15\"", 0);

		repository.insertFeed(1L, request);

		ArgumentCaptor<PreparedStatementCreator> creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
		verify(jdbcTemplate).update(creator.capture(), any(KeyHolder.class));
		Connection connection = mock(Connection.class);
		PreparedStatement preparedStatement = mock(PreparedStatement.class);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		when(connection.prepareStatement(sql.capture(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(preparedStatement);

		creator.getValue().createPreparedStatement(connection);

		ArgumentCaptor<String> contentText = ArgumentCaptor.forClass(String.class);
		assertThat(sql.getValue()).contains("running_record_id");
		verify(preparedStatement).setObject(2, null);
		verify(preparedStatement).setString(eq(5), contentText.capture());
		assertThat(contentText.getValue()).contains("---NEOSTRIDE-METRICS---", "3.2", "20:00", "6'15\"");
	}


	@Test
	void insertFeed_persistsImagesToNormalizedImageTable() {
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 10L));
			return 1;
		}).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
		FeedUploadRequest request = new FeedUploadRequest("title", "body", "PUBLIC", false, null, List.of(), List.of(" /uploads/community/a.jpg ", "/uploads/community/b.jpg"), null, null, null, 0);

		repository.insertFeed(1L, request);

		verify(jdbcTemplate).update(anyString(), eq(10L), eq(0), eq("/uploads/community/a.jpg"));
		verify(jdbcTemplate).update(anyString(), eq(10L), eq(1), eq("/uploads/community/b.jpg"));
	}

	@Test
	void listFeeds_mapsStoredMetricsWhenRunningRecordIsNotLinked() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<FeedUploadResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(feedRowWithStoredMetrics(), 0));
		});

		List<FeedUploadResponse> feeds = repository.listFeeds();

		assertThat(feeds).hasSize(1);
		FeedUploadResponse feed = feeds.getFirst();
		assertThat(feed.distance()).isEqualTo("3.20 km");
		assertThat(feed.duration()).isEqualTo("20:00");
		assertThat(feed.pace()).isEqualTo("6'15\"");
		assertThat(feed.routeMapImageUri()).isEqualTo("route.png");
	}

	@Test
	void listFeedsWithViewer_projectsWriterIdMineAndPublicImageFields() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<FeedUploadResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(feedRowWithViewerMetadata(), 0));
		});

		List<FeedUploadResponse> feeds = repository.listFeeds(1L);

		assertThat(feeds).hasSize(1);
		FeedUploadResponse feed = feeds.getFirst();
		assertThat(feed.writerId()).isEqualTo(1L);
		assertThat(feed.mine()).isTrue();
		assertThat(feed.profileImageUrl()).isEqualTo("/uploads/profile/me.jpg");
		assertThat(feed.imageUrls()).containsExactly("/uploads/feed/body.jpg");
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(sql.getValue()).contains("cc.author_user_id", "AS mine");
	}

	@Test
	void myFeeds_projectsProfileImageAndContentImagesForMiniView() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<CommunityContentResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(contentRowWithImages(), 0));
		});

		CommunityContentResponse content = repository.myFeeds(1L).getFirst();

		assertThat(content.profileImageUrl()).isEqualTo("/uploads/profile/me.jpg");
		assertThat(content.imageUrls()).containsExactly("/uploads/feed/body.jpg");
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
	void listTipsWithViewer_usesContentStatsTableForCounts() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.listTips(1L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(sql.getValue()).contains("LEFT JOIN community_content_stats stats");
		assertThat(sql.getValue()).doesNotContain("SELECT COUNT(*) FROM community_interactions ci WHERE ci.content_id=cc.content_id");
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
	void findTipDetail_decodesCourseAddress() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			RowMapper<com.neostride.server.community.dto.TipDetailResponse> mapper = invocation.getArgument(1);
			if (sql.contains("WHERE cc.content_type='TIP'")) {
				return List.of(mapper.mapRow(tipRowWithCourseAddress(), 0));
			}
			return List.of();
		});

		var detail = repository.findTipDetail(1L, 7L);

		assertThat(detail.routeMapImageUrl()).isEqualTo("route.png");
		assertThat(detail.courseAddress()).isEqualTo("Seoul Forest");
	}

	@Test
	void findTipDetailFiltersBlockedCommentAuthors() throws Exception {
		List<String> commentQueries = new java.util.ArrayList<>();
		List<Object[]> commentArgs = new java.util.ArrayList<>();
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			if (sql.contains("WHERE cc.content_type='TIP'")) {
				RowMapper<com.neostride.server.community.dto.TipDetailResponse> mapper = invocation.getArgument(1);
				return List.of(mapper.mapRow(tipRow("COURSE"), 0));
			}
			if (sql.contains("ci.interaction_type='COMMENT'")) {
				commentQueries.add(sql);
				commentArgs.add(java.util.Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length));
			}
			return List.of();
		});

		repository.findTipDetail(1L, 7L);

		assertThat(commentQueries).singleElement().satisfies(sql -> assertThat(sql)
				.contains("NOT EXISTS", "relationships r", "r.user1_id=?", "r.user2_id=ci.user_id", "r.status='BLOCKED'"));
		assertThat(commentArgs).singleElement().satisfies(args -> assertThat(args).containsExactly(7L, 1L));
	}

	@Test
	//lineA
	void listTips_mapsLegacyEtcTipTypeToFreeCategoryForClient() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<com.neostride.server.community.dto.TipUploadResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(tipRow("ETC"), 0));
		});

		var tips = repository.listTips();

		assertThat(tips).hasSize(1);
		assertThat(tips.getFirst().category()).isEqualTo("FREE");
	}


	@Test
	void myFeeds_usesRunningRecordMetricsWhenLinked() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<CommunityContentResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(contentRowWithRunningRecord(), 0));
		});

		List<CommunityContentResponse> feeds = repository.myFeeds(1L);
		assertThat(feeds).hasSize(1);
		CommunityContentResponse content = feeds.getFirst();
		assertThat(content.totalDistance()).isEqualByComparingTo("7.30");
		assertThat(content.duration()).isEqualTo(1234);
		assertThat(content.pace()).isEqualTo(385);
		assertThat(content.contentText()).isEqualTo("run linked");
	}

	@Test
	void myFeedsKeepsIntegerDecimalRunningPaceAsSeconds() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<CommunityContentResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(contentRowWithLinkedPace(new BigDecimal("330.00")), 0));
		});

		CommunityContentResponse content = repository.myFeeds(1L).getFirst();

		assertThat(content.pace()).isEqualTo(330);
	}

	@Test
	void listFeeds_formatsLinkedRunningPaceStoredAsMinuteSecondDecimal() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<FeedUploadResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(feedRowWithLinkedPace(new BigDecimal("6.45")), 0));
		});

		FeedUploadResponse feed = repository.listFeeds().getFirst();

		assertThat(feed.pace()).isEqualTo("6'45\"");
	}

	@Test
	void myFeeds_fallsBackToEncodedMetricsWhenRunningRecordIsNotLinked() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<CommunityContentResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(contentRowWithEncodedMetrics(), 0));
		});

		List<CommunityContentResponse> feeds = repository.myFeeds(1L);
		assertThat(feeds).hasSize(1);
		CommunityContentResponse content = feeds.getFirst();
		assertThat(content.totalDistance()).isEqualByComparingTo("3.2");
		assertThat(content.duration()).isEqualTo(1200);
		assertThat(content.pace()).isEqualTo(375);
		assertThat(content.contentText()).isEqualTo("run encoded");
	}

	@Test
	void myFeedsParsesSlashKmPaceSuffixFromEncodedMetrics() throws Exception {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<CommunityContentResponse> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(contentRowWithEncodedPace("5:37/km"), 0));
		});

		CommunityContentResponse content = repository.myFeeds(1L).getFirst();

		assertThat(content.pace()).isEqualTo(337);
	}

	@Test
	void updateRelationshipDeleteRemovesNonBlockedRelationshipInEitherDirection() {
		repository.updateRelationship(1L, new com.neostride.server.community.dto.FriendRequest(2L, "delete"));

		verify(jdbcTemplate).update(
				"DELETE FROM relationships WHERE status <> 'BLOCKED' AND ((user1_id=? AND user2_id=?) OR (user1_id=? AND user2_id=?))",
				1L, 2L, 2L, 1L
		);
	}

	@Test
	void updateRelationshipUnblockRemovesCurrentUserBlockedRelationship() {
		repository.updateRelationship(1L, new com.neostride.server.community.dto.FriendRequest(2L, "unblock"));

		verify(jdbcTemplate).update(
				"DELETE FROM relationships WHERE user1_id=? AND user2_id=? AND status='BLOCKED'",
				1L, 2L
		);
	}

	@Test
	void updateRelationshipBlockReplacesExistingPairWithCurrentUserBlock() {
		repository.updateRelationship(1L, new com.neostride.server.community.dto.FriendRequest(2L, "block"));

		verify(jdbcTemplate).update(
				"DELETE FROM relationships WHERE (user1_id=? AND user2_id=?) OR (user1_id=? AND user2_id=?)",
				1L, 2L, 2L, 1L
		);
		verify(jdbcTemplate).update("INSERT INTO relationships (user1_id, user2_id, status) VALUES (?, ?, 'BLOCKED')", 1L, 2L);
		verify(jdbcTemplate).update("""
					DELETE ci FROM community_interactions ci
					JOIN community_contents cc ON cc.content_id = ci.content_id
					WHERE ci.user_id = ? AND cc.author_user_id = ?
					""", 1L, 2L);
	}

	@Test
	void getUserProfileProjectsViewerRelationshipFlags() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.getUserProfile(1L, 2L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(1L), eq(1L), eq(1L), eq(1L), eq(2L));
		assertThat(sql.getValue()).contains("AS is_friend", "AS is_blocked", "AS is_sent", "r.status = 'BLOCKED' AND r.user1_id = ?", "r.status = 'REQUESTED' AND r.user1_id = ?");
	}

	@Test
	void searchProfilesWithViewerProjectsRelationshipStatus() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.searchProfiles(1L, "neo", 0, 10);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue()).contains("AS relationship_status", "THEN 'sent'", "THEN 'received'", "r.status='REQUESTED' AND r.user1_id=?");
		assertThat(args.getValue()).containsExactly(1L, 1L, 1L, 1L, 1L, "%neo%", 1L, 1L, 10, 0);
	}

	@Test
	void getFriendListSentFiltersRequestsFromCurrentUserOnly() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.getFriendList(1L, "sent");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue()).contains("r.user1_id = ?", "r.status = 'REQUESTED'");
		assertThat(sql.getValue()).doesNotContain("r.user1_id = ? OR r.user2_id = ?");
		assertThat(args.getValue()).containsExactly(1L, 1L);
	}

	@Test
	void getFriendListReceivedFiltersRequestsToCurrentUserOnly() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.getFriendList(1L, "received");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
		assertThat(sql.getValue()).contains("r.user2_id = ?", "r.status = 'REQUESTED'");
		assertThat(sql.getValue()).doesNotContain("r.user1_id = ? OR r.user2_id = ?");
		assertThat(args.getValue()).containsExactly(1L, 1L);
	}

	private ResultSet contentRowWithRunningRecord() throws Exception {
		return contentRowWithLinkedPace(new BigDecimal("6.25"));
	}

	private ResultSet contentRowWithLinkedPace(BigDecimal pace) throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("content_id")).thenReturn(1L);
		when(rs.getString("content_text")).thenReturn("title\n---NEOSTRIDE-FEED---\nrun linked\n---NEOSTRIDE-ROUTE---\n");
		when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf("2026-05-11 00:00:00"));
		when(rs.getObject("joined_running_record_id")).thenReturn(10L);
		when(rs.getBigDecimal("total_distance")).thenReturn(new BigDecimal("7.30"));
		when(rs.getObject("duration")).thenReturn(1234);
		when(rs.getObject("pace")).thenReturn(pace);
		return rs;
	}

	private ResultSet contentRowWithEncodedMetrics() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("content_id")).thenReturn(2L);
		when(rs.getString("content_text")).thenReturn("title\n---NEOSTRIDE-FEED---\nrun encoded\n---NEOSTRIDE-ROUTE---\n\n---NEOSTRIDE-METRICS---\n3.2\n---NEOSTRIDE-METRIC---\n20:00\n---NEOSTRIDE-METRIC---\n6\u002715\"");
		when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf("2026-05-11 00:00:00"));
		when(rs.getObject("joined_running_record_id")).thenReturn(null);
		when(rs.getBigDecimal("total_distance")).thenReturn(BigDecimal.ZERO);
		when(rs.getObject("duration")).thenReturn(null);
		when(rs.getObject("pace")).thenReturn(null);
		return rs;
	}

	private ResultSet contentRowWithEncodedPace(String pace) throws Exception {
		ResultSet rs = contentRowWithEncodedMetrics();
		when(rs.getString("content_text")).thenReturn("""
			title
			---NEOSTRIDE-FEED---
			run encoded
			---NEOSTRIDE-ROUTE---

			---NEOSTRIDE-METRICS---
			3.2
			---NEOSTRIDE-METRIC---
			20:00
			---NEOSTRIDE-METRIC---
			""" + pace);
		return rs;
	}


	private ResultSet feedRowWithStoredMetrics() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong("content_id")).thenReturn(10L);
		when(rs.getString("profile_image_url")).thenReturn("photo.png");
		when(rs.getString("nickname")).thenReturn("neo");
		when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf("2026-05-11 00:00:00"));
		when(rs.getString("content_text")).thenReturn("title\n---NEOSTRIDE-FEED---\nbody\n---NEOSTRIDE-ROUTE---\nroute.png\n---NEOSTRIDE-METRICS---\n3.2\n---NEOSTRIDE-METRIC---\n20:00\n---NEOSTRIDE-METRIC---\n6'15\"");
		when(rs.getObject("joined_running_record_id")).thenReturn(null);
		when(rs.getBigDecimal("total_distance")).thenReturn(BigDecimal.ZERO);
		when(rs.getObject("duration")).thenReturn(null);
		when(rs.getObject("pace")).thenReturn(null);
		when(rs.getInt("tagged_count")).thenReturn(0);
		when(rs.getInt("like_count")).thenReturn(0);
		when(rs.getInt("comment_count")).thenReturn(0);
		when(rs.getBoolean("include_route_detail")).thenReturn(true);
		when(rs.getString("image")).thenReturn(null);
		return rs;
	}

	private ResultSet feedRowWithViewerMetadata() throws Exception {
		ResultSet rs = feedRowWithStoredMetrics();
		when(rs.getLong("author_user_id")).thenReturn(1L);
		when(rs.getBoolean("mine")).thenReturn(true);
		when(rs.getString("profile_image_url")).thenReturn("/uploads/profile/me.jpg");
		when(rs.getString("image")).thenReturn("/uploads/feed/body.jpg");
		return rs;
	}

	private ResultSet feedRowWithLinkedPace(BigDecimal pace) throws Exception {
		ResultSet rs = feedRowWithStoredMetrics();
		when(rs.getString("content_text")).thenReturn("title\n---NEOSTRIDE-FEED---\nbody\n---NEOSTRIDE-ROUTE---\nroute.png");
		when(rs.getObject("joined_running_record_id")).thenReturn(99L);
		when(rs.getBigDecimal("total_distance")).thenReturn(new BigDecimal("5.00"));
		when(rs.getObject("duration")).thenReturn(2025);
		when(rs.getObject("pace")).thenReturn(pace);
		return rs;
	}

	private ResultSet contentRowWithImages() throws Exception {
		ResultSet rs = contentRowWithRunningRecord();
		when(rs.getString("profile_image_url")).thenReturn("/uploads/profile/me.jpg");
		when(rs.getString("image")).thenReturn("/uploads/feed/body.jpg");
		return rs;
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

	private ResultSet tipRowWithCourseAddress() throws Exception {
		ResultSet rs = tipRow("COURSE");
		when(rs.getString("content_text")).thenReturn("title\n---NEOSTRIDE-TIP---\nbody\n---NEOSTRIDE-ROUTE---\nroute.png\n---NEOSTRIDE-ADDR---\nSeoul Forest");
		when(rs.getBoolean("include_route_detail")).thenReturn(true);
		return rs;
	}


	private com.neostride.server.community.dto.CommentResponse commentRow(long commentId, long writerId, String content) {
		return new com.neostride.server.community.dto.CommentResponse(commentId, writerId, "neo", null, content, "2026-05-11T00:00:00", false, "NONE", true);
	}
}

