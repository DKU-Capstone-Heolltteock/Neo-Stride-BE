package com.neostride.server.community.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.community.dto.BadgeDetailResponse;
import com.neostride.server.community.dto.CommentResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedCursorResponse;
import com.neostride.server.community.dto.FeedPageResponse;
import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendRequest;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.community.dto.SearchUserResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.community.service.CommunityService;
import com.neostride.server.storage.StorageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityControllerTest {

	private static final String AUTHORIZATION = "Bearer access-token";

	private final CommunityService service = mock(CommunityService.class);
	private final AuthenticatedUserService authenticatedUserService = mock(AuthenticatedUserService.class);
	private final CommunityUserContext userContext = new CommunityUserContext(authenticatedUserService);
	private final StorageService storageService = mock(StorageService.class);
	private final CommunityMultipartSupport uploadSupport = new CommunityMultipartSupport(storageService);
	private final CommunityProfileController profileController = new CommunityProfileController(service, userContext, uploadSupport);
	private final CommunityRelationshipController relationshipController = new CommunityRelationshipController(service, userContext);
	private final CommunityFeedController feedController = new CommunityFeedController(service, userContext, uploadSupport);
	private final CommunityTipController tipController = new CommunityTipController(service, userContext, uploadSupport);
	private final CommunitySearchController searchController = new CommunitySearchController(service, userContext);

	@Test
	void getUserProfile_returnsAuthenticatedUserProfile() {
		UserProfileResponse body = new UserProfileResponse("neo", "photo.png", "running", false, false, 2, 3, 4, 5, 6, 7);
		authenticate();
		when(service.getUserProfile(1L)).thenReturn(body);

		var response = profileController.getUserProfile(AUTHORIZATION, 1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(body);
	}

	@Test
	void updateStatusMessage_returnsNoContent() {
		authenticate();

		var response = profileController.updateStatusMessage(AUTHORIZATION, 1L, Map.of("status_message", "ready"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void getMyPageContentLists_returnOkLists() {
		List<CommunityContentResponse> body = List.of(new CommunityContentResponse(10L, "text", new BigDecimal("3.2"), 1200, 6, "2026-05-11T00:00:00"));
		when(service.getMyFeeds(1L)).thenReturn(body);
		when(service.getTaggedFeeds(1L)).thenReturn(body);
		when(service.getCommentedFeeds(1L)).thenReturn(body);
		when(service.getLikedFeeds(1L)).thenReturn(body);
		when(service.getBookmarkedFeeds(1L)).thenReturn(body);
		authenticate();

		assertThat(feedController.getMyFeeds(AUTHORIZATION, 1L).getBody()).isSameAs(body);
		assertThat(feedController.getTaggedFeeds(AUTHORIZATION, 1L).getBody()).isSameAs(body);
		assertThat(feedController.getCommentedFeeds(AUTHORIZATION, 1L).getBody()).isSameAs(body);
		assertThat(feedController.getLikedFeeds(AUTHORIZATION, 1L).getBody()).isSameAs(body);
		assertThat(feedController.getBookmarkedFeeds(AUTHORIZATION, 1L).getBody()).isSameAs(body);
	}

	@Test
	void getBadgeDetail_returnsOkBadge() {
		BadgeDetailResponse body = new BadgeDetailResponse("GOLD", 11L, new BigDecimal("10.0"), "5'30\"", "2026-05-11T00:00:00");
		authenticate();
		when(service.getBadgeDetail(1L)).thenReturn(body);

		var response = profileController.getBadgeDetail(AUTHORIZATION, 1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(body);
	}

	@Test
	void friendApis_supportCommunityAndLegacyRelationshipPaths() {
		List<FriendResponse> friends = List.of(new FriendResponse(2L, "runner", "SILVER", 3, "photo.png", "friends"));
		FriendRequest request = new FriendRequest(2L, "accept");
		when(service.getFriendList(1L, "friends")).thenReturn(friends);
		when(service.updateRelationship(1L, request)).thenReturn(Map.of("status", "success"));
		authenticate();

		assertThat(relationshipController.getCommunityFriends(AUTHORIZATION, 1L, "friends").getBody()).isSameAs(friends);
		assertThat(relationshipController.getLegacyRelationships(AUTHORIZATION, 1L, "friends").getBody()).isSameAs(friends);
		assertThat(relationshipController.updateCommunityRelationship(AUTHORIZATION, 1L, request).getBody()).containsEntry("status", "success");
		assertThat(relationshipController.updateLegacyRelationship(AUTHORIZATION, 1L, request).getBody()).containsEntry("status", "success");
	}

	@Test
	void feedApis_uploadAndListFeeds() {
		FeedUploadRequest request = new FeedUploadRequest("title", "content", "PUBLIC", true, "route.png", List.of(2L), List.of("image.png"), new BigDecimal("3.2"), "20:00", "6'15\"", 1);
		FeedUploadResponse uploaded = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", "content", 1, 0, 0, "3.20 km", "20:00", "6'15\"", true, "route.png", List.of("image.png"));
		when(service.uploadFeed(1L, request)).thenReturn(uploaded);
		when(service.getFeedList()).thenReturn(List.of(uploaded));
		authenticate();

		assertThat(feedController.uploadFeed(AUTHORIZATION, 1L, request).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(feedController.getFeedList().getBody()).containsExactly(uploaded);
	}

	@Test
	void accountApis_supportCurrentUserAccountNicknameAndDeletion() {
		var account = new com.neostride.server.community.dto.AccountInfoResponse("runner@example.com", "neo", "photo.png");
		authenticate();
		when(service.getAccountInfo(1L)).thenReturn(account);

		assertThat(profileController.getAccountInfo(AUTHORIZATION, 1L).getBody()).isSameAs(account);
		assertThat(profileController.updateNickname(AUTHORIZATION, 1L, Map.of("nickname", "neo2")).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(profileController.deleteAccount(AUTHORIZATION, 1L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void updateProfileImage_rejectsEmptyRequestWithoutOverwritingExistingPhoto() {
		authenticate();

		assertThatThrownBy(() -> profileController.updateProfileImage(AUTHORIZATION, 1L, " ", null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("profile_image_url, image 또는 profile_photo");

		verify(service, never()).updateProfileImage(anyLong(), any());
	}

	@Test
	void updateProfileImage_storesMultipartImageUrl() {
		MockMultipartFile image = new MockMultipartFile("image", "profile.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});
		authenticate();
		when(storageService.storeImage(image, "profile")).thenReturn("/uploads/profile/profile-id.png");

		var response = profileController.updateProfileImage(AUTHORIZATION, 1L, null, image, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(service).updateProfileImage(1L, "/uploads/profile/profile-id.png");
	}

	@Test
	void updateProfileImage_acceptsProfilePhotoMultipartAlias() {
		MockMultipartFile image = new MockMultipartFile("profile_photo", "profile.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});
		authenticate();
		when(storageService.storeImage(image, "profile")).thenReturn("/uploads/profile/profile-id.png");

		var response = profileController.updateProfileImage(AUTHORIZATION, 1L, null, null, image);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(storageService).storeImage(image, "profile");
		verify(service).updateProfileImage(1L, "/uploads/profile/profile-id.png");
	}

	@Test
	void deleteProfileImage_resetsStoredProfilePhoto() {
		authenticate();

		var response = profileController.deleteProfileImage(AUTHORIZATION, 1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(service).deleteProfileImage(1L);
	}

	@Test
	void uploadFeedMultipart_storesSingleImageAndRouteImageBeforeServiceCall() {
		MockMultipartFile image = new MockMultipartFile("images", "feed.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
		MockMultipartFile route = new MockMultipartFile("route_image", "route.webp", "image/webp", new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});
		FeedUploadRequest expected = new FeedUploadRequest("title", "content", "PUBLIC", true, "/uploads/routes/route-id.webp", List.of(2L), List.of("/uploads/community/feed-id.jpg"), new BigDecimal("3.2"), "20:00", "6'15\"", 1);
		FeedUploadResponse uploaded = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", "content", 1, 0, 0, "3.20 km", "20:00", "6'15\"", true, "/uploads/routes/route-id.webp", List.of("/uploads/community/feed-id.jpg"));
		authenticate();
		when(storageService.storeImage(image, "community")).thenReturn("/uploads/community/feed-id.jpg");
		when(storageService.storeImage(route, "routes")).thenReturn("/uploads/routes/route-id.webp");
		when(service.uploadFeed(1L, expected)).thenReturn(uploaded);

		var response = feedController.uploadFeedMultipart(AUTHORIZATION, 1L, Map.of("title", "title", "content", "content", "privacy", "PUBLIC", "mapVisible", "true", "taggedUserIds", "2", "distance", "3.2", "runningTime", "20:00", "pace", "6'15\"", "tagCount", "1"), List.of(image), route, null, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isSameAs(uploaded);
		verify(service).uploadFeed(1L, expected);
	}

	@Test
	void uploadFeedMultipart_acceptsJsonArrayTaggedUserIdsFromAndroid() {
		MockMultipartFile route = new MockMultipartFile("routeMapImage", "route.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
		FeedUploadRequest expected = new FeedUploadRequest("title", "content", "PUBLIC", true, "/uploads/routes/route-id.png", List.of(2L, 3L), List.of(), null, "20:00", "6'15\"", 2);
		FeedUploadResponse uploaded = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", "content", 2, 0, 0, "0.00 km", "20:00", "6'15\"", true, "/uploads/routes/route-id.png", List.of());
		authenticate();
		when(storageService.storeImage(route, "routes")).thenReturn("/uploads/routes/route-id.png");
		when(service.uploadFeed(1L, expected)).thenReturn(uploaded);

		var response = feedController.uploadFeedMultipart(AUTHORIZATION, 1L, Map.of("title", "title", "content", "content", "privacy", "PUBLIC", "mapVisible", "true", "taggedUserIds", "[2,3]", "runningTime", "20:00", "pace", "6'15\"", "tagCount", "2"), null, null, route, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		verify(service).uploadFeed(1L, expected);
	}

	@Test
	void uploadFeedMultipart_supportsAndroidAliasesForMetricsAndRouteImageField() {
		MockMultipartFile image = new MockMultipartFile("images", "feed.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
		MockMultipartFile route = new MockMultipartFile("route_map_image", "route.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
		FeedUploadRequest expected = new FeedUploadRequest("title", "content", "PUBLIC", true, "/uploads/routes/route-id.png", List.of(2L, 3L), List.of("/uploads/community/feed-id.jpg"), new BigDecimal("3.2"), "20:00", "6'15\"", 1);
		FeedUploadResponse uploaded = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", "content", 1, 0, 0, "3.20 km", "20:00", "6'15\"", true, "/uploads/routes/route-id.png", List.of("/uploads/community/feed-id.jpg"));
		authenticate();
		when(storageService.storeImage(image, "community")).thenReturn("/uploads/community/feed-id.jpg");
		when(storageService.storeImage(route, "routes")).thenReturn("/uploads/routes/route-id.png");
		when(service.uploadFeed(1L, expected)).thenReturn(uploaded);

		var response = feedController.uploadFeedMultipart(
				AUTHORIZATION, 1L,
				Map.of("title", "title", "content", "content", "privacy", "PUBLIC", "map_visible", "true", "tagged_user_ids", "[2,3]", "total_distance", "3.2", "duration", "20:00", "running_pace", "6'15\"", "tag_count", "1"),
				List.of(image),
				null,
				null,
				route
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isSameAs(uploaded);
		verify(service).uploadFeed(1L, expected);
	}

	@Test
	void uploadFeedMultipart_mapsRunningRecordIdFromSnakeCaseAlias() {
		FeedUploadRequest expected = new FeedUploadRequest("title", "content", "PUBLIC", true, null, List.of(), List.of(), null, "30:00", null, 0, 555L);
		FeedUploadResponse uploaded = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", "content", 0, 0, 0, "0.00 km", "30:00", null, true, null, List.of());
		authenticate();
		when(service.uploadFeed(1L, expected)).thenReturn(uploaded);

		var response = feedController.uploadFeedMultipart(
				AUTHORIZATION,
				1L,
				Map.of("title", "title", "content", "content", "privacy", "PUBLIC", "mapVisible", "true", "duration", "30:00", "run_record_id", "555"),
				null,
				null,
				null,
				null
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isSameAs(uploaded);
		verify(service).uploadFeed(1L, expected);
	}

	@Test
	void uploadFeedMultipart_storesMultipleImagesBeforeServiceCall() {
		MockMultipartFile first = new MockMultipartFile("images", "first.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
		MockMultipartFile second = new MockMultipartFile("images", "second.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
		FeedUploadRequest expected = new FeedUploadRequest("title", null, null, false, null, List.of(), List.of("/uploads/community/first.jpg", "/uploads/community/second.jpg"), null, null, null, 0);
		FeedUploadResponse uploaded = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", null, 0, 0, 0, "0.00 km", null, null, false, null, List.of("/uploads/community/first.jpg", "/uploads/community/second.jpg"));
		authenticate();
		when(storageService.storeImage(first, "community")).thenReturn("/uploads/community/first.jpg");
		when(storageService.storeImage(second, "community")).thenReturn("/uploads/community/second.jpg");
		when(service.uploadFeed(1L, expected)).thenReturn(uploaded);

		var response = feedController.uploadFeedMultipart(AUTHORIZATION, 1L, Map.of("title", "title"), List.of(first, second), null, null, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isSameAs(uploaded);
		verify(service).uploadFeed(1L, expected);
	}

	@Test
	void runnerPageApis_returnOtherUserProfileBadgeFeedsAndFriends() {
		UserProfileResponse profile = new UserProfileResponse("runner", "photo.png", "ready", true, false, 2, 3, 0, 0, 0, 0);
		BadgeDetailResponse badge = new BadgeDetailResponse("GOLD", 11L, new BigDecimal("10.0"), "5", "2026-05-11T00:00:00");
		List<CommunityContentResponse> feeds = List.of(new CommunityContentResponse(10L, "text", new BigDecimal("3.2"), 1200, 6, "2026-05-11T00:00:00"));
		List<FriendResponse> friends = List.of(new FriendResponse(3L, "friend", "SILVER", 4, "friend.png", "friends"));
		authenticate();
		when(service.getUserProfile(1L, 2L)).thenReturn(profile);
		when(service.getBadgeDetail(2L)).thenReturn(badge);
		when(service.getUserFeeds(1L, 2L)).thenReturn(feeds);
		when(service.getUserFriendList(2L)).thenReturn(friends);
		when(service.getUserFriendList(1L, 2L)).thenReturn(friends);

		assertThat(profileController.getRunnerProfile(AUTHORIZATION, 1L, 2L).getBody()).isSameAs(profile);
		assertThat(profileController.getUserBadgeDetail(2L).getBody()).isSameAs(badge);
		assertThat(feedController.getRunnerFeeds(AUTHORIZATION, 1L, 2L).getBody()).isSameAs(feeds);
		assertThat(relationshipController.getUserFriendList(AUTHORIZATION, 1L, 2L).getBody()).isSameAs(friends);
		assertThat(relationshipController.getApiUserFriendList(AUTHORIZATION, 1L, 2L).getBody()).isSameAs(friends);
	}

	@Test
	void bookmarkAndTipApis_supportAndroidContracts() {
		var bookmark = Map.of("status", "success", "bookmarked", "true");
		var tipRequest = new com.neostride.server.community.dto.TipUploadRequest("COURSE", "title", "content", true, "route.png", List.of("tip.png"));
		var tip = new com.neostride.server.community.dto.TipUploadResponse(7L, "neo", "photo.png", true, "COURSE", "title", "content", true, "route.png", List.of("tip.png"), 1, 2, "2026-05-11T00:00:00");
		var tipList = new com.neostride.server.community.dto.TipListResponse(List.of(tip));
		when(service.toggleBookmark(1L, 10L)).thenReturn(bookmark);
		when(service.uploadTip(1L, tipRequest)).thenReturn(tip);
		when(service.getTips(1L)).thenReturn(tipList);
		authenticate();

		assertThat(feedController.toggleBookmark(AUTHORIZATION, 1L, 10L).getBody()).isSameAs(bookmark);
		assertThat(tipController.uploadTip(AUTHORIZATION, 1L, tipRequest).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(tipController.uploadTip(AUTHORIZATION, 1L, tipRequest).getBody()).isSameAs(tip);
		assertThat(tipController.getTips(AUTHORIZATION, 1L).getBody()).isSameAs(tipList);
	}


	@Test
	void getCommunityFeedList_ignoresUnauthenticatedUserIdHeader() {
		FeedUploadResponse feed = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", "content", 1, 0, 0, "3.20 km", "20:00", "6'15\"", true, "route.png", List.of("image.png"));
		when(service.getFeedList((Long) null, null, null, null)).thenReturn(List.of(feed));

		var response = feedController.getCommunityFeedList(null, 1L, null, null, null, null, null);

		assertThat(response.getBody()).containsExactly(feed);
		verify(service).getFeedList((Long) null, null, null, null);
		verify(authenticatedUserService, never()).requireUserId(any());
	}

	@Test
	void getCommunityFeedPage_supportsCursorAliasesWithoutChangingLegacyList() {
		FeedUploadResponse feed = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", "content", 1, 0, 0, "3.20 km", "20:00", "6'15\"", true, "route.png", List.of("image.png"));
		FeedPageResponse page = new FeedPageResponse(List.of(feed), new FeedCursorResponse("2026-05-11T00:00:00", 99L), true);
		authenticate();
		when(service.getFeedPage(1L, "2026-05-26T22:14:32", 76L, 10)).thenReturn(page);

		var response = feedController.getCommunityFeedPage(AUTHORIZATION, 1L, 10, null, "2026-05-26T22:14:32", null, 76L);

		assertThat(response.getBody()).isSameAs(page);
		verify(service).getFeedPage(1L, "2026-05-26T22:14:32", 76L, 10);
	}


	@Test
	void commentPageApis_supportCursorAliases() {
		CommentResponse comment = new CommentResponse(5L, 2L, "runner", null, "nice", "2026-05-28T10:00:00", false, "NONE", false);
		var page = new com.neostride.server.community.dto.CommentPageResponse(
				List.of(comment),
				new com.neostride.server.community.dto.CommentCursorResponse("2026-05-28T10:00:00", 5L),
				true
		);
		authenticate();
		when(service.getFeedComments(1L, 99L, "2026-05-28T09:00:00", 4L, 10)).thenReturn(page);
		when(service.getTipComments(1L, 7L, "2026-05-28T09:00:00", 4L, 10)).thenReturn(page);

		assertThat(feedController.getFeedComments(AUTHORIZATION, 1L, 99L, 10, null, "2026-05-28T09:00:00", null, 4L).getBody()).isSameAs(page);
		assertThat(tipController.getTipComments(AUTHORIZATION, 1L, 7L, 10, null, "2026-05-28T09:00:00", null, 4L).getBody()).isSameAs(page);
	}

	@Test
	void searchApis_supportFeedTipProfileFriendContracts() {
		FeedUploadResponse feed = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", "content", 1, 0, 0, "3.20 km", "20:00", "6'15\"", true, "route.png", List.of("image.png"));
		var tip = new com.neostride.server.community.dto.TipUploadResponse(7L, "neo", "photo.png", true, "COURSE", "title", "content", true, "route.png", List.of("tip.png"), 1, 2, "2026-05-11T00:00:00");
		SearchUserResponse user = new SearchUserResponse(2L, "runner", "profile.png", "ready", 3, "GOLD", "friends");
		when(service.searchFeeds("run", 0, 10)).thenReturn(List.of(feed));
		when(service.searchTips("pace", "ALL", 0, 10)).thenReturn(List.of(tip));
		when(service.searchProfiles("neo", 0, 10)).thenReturn(List.of(user));
		when(service.searchFriends(1L, "neo")).thenReturn(List.of(user));
		when(service.getMyFriends(1L)).thenReturn(List.of(user));
		authenticate();

		assertThat(searchController.searchFeeds("run", 0, 10).getBody()).containsExactly(feed);
		assertThat(searchController.searchTips("pace", "ALL", 0, 10).getBody()).containsExactly(tip);
		assertThat(searchController.searchProfiles("neo", 0, 10).getBody()).containsExactly(user);
		assertThat(searchController.searchFriends(AUTHORIZATION, 1L, "neo").getBody()).containsExactly(user);
		assertThat(searchController.getTopProfiles(0, 10).getBody()).isEmpty();
		assertThat(searchController.getMyFriends(AUTHORIZATION, 1L).getBody()).containsExactly(user);
	}

	@Test
	void missingFeedApis_supportAndroidContracts() {
		FeedUploadRequest update = new FeedUploadRequest("updated", "body", "PUBLIC", true, "route.png", List.of(2L), List.of("image.png"), new BigDecimal("5.0"), "30:00", "6'00\"", 1);
		FeedUploadResponse feed = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "updated", "body", 1, 1, 1, "5.00 km", "30:00", "6'00\"", true, "route.png", List.of("image.png"));
		var detail = new com.neostride.server.community.dto.FeedDetailResponse(99L, 1L, null, "neo", false, "NONE", "2026-05-11T00:00:00", "updated", "body", 1, 1, 1, true, false, true, "5.00 km", "30:00", "6'00\"", true, "route.png", List.of("image.png"), List.of());
		var commentRequest = new com.neostride.server.community.dto.CommentRequest("hello");
		var comment = new com.neostride.server.community.dto.CommentResponse(5L, 1L, "neo", null, "hello", "2026-05-11T00:00:00", false, "NONE", true);
		List<FriendResponse> tagged = List.of(new FriendResponse(2L, "runner", "SILVER", 3, "photo.png", "tagged"));
		authenticate();
		when(service.getFeedDetail(1L, 99L)).thenReturn(detail);
		when(service.toggleFeedLike(1L, 99L)).thenReturn(Map.of("liked", "true", "likeCount", "1"));
		when(service.toggleFeedBookmark(1L, 99L)).thenReturn(Map.of("bookmarked", "true"));
		when(service.createFeedComment(1L, 99L, commentRequest)).thenReturn(comment);
		when(service.updateFeed(1L, 99L, update)).thenReturn(feed);
		when(service.updateFeedComment(1L, 99L, 5L, commentRequest)).thenReturn(comment);
		when(service.getTaggedUsers(99L)).thenReturn(tagged);

		assertThat(feedController.getFeedDetail(AUTHORIZATION, 1L, 99L).getBody()).isSameAs(detail);
		assertThat(feedController.toggleFeedLike(AUTHORIZATION, 1L, 99L).getBody()).containsEntry("liked", "true");
		assertThat(feedController.toggleFeedBookmark(AUTHORIZATION, 1L, 99L).getBody()).containsEntry("bookmarked", "true");
		assertThat(feedController.createFeedComment(AUTHORIZATION, 1L, 99L, commentRequest).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(feedController.updateFeed(AUTHORIZATION, 1L, 99L, update).getBody()).isSameAs(feed);
		assertThat(feedController.updateFeedComment(AUTHORIZATION, 1L, 99L, 5L, commentRequest).getBody()).isSameAs(comment);
		assertThat(feedController.deleteFeedComment(AUTHORIZATION, 1L, 99L, 5L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(feedController.deleteFeed(AUTHORIZATION, 1L, 99L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(feedController.getTaggedUsers(99L).getBody()).containsExactlyElementsOf(tagged);
	}

	@Test
	void uploadTipMultipart_mapsCourseAddress() {
		var expected = new com.neostride.server.community.dto.TipUploadRequest("COURSE", "title", "content", true, null, "Seoul Forest", List.of());
		var uploaded = new com.neostride.server.community.dto.TipUploadResponse(7L, "neo", "photo.png", true, "COURSE", "title", "content", true, null, List.of(), 0, 0, "2026-05-11T00:00:00");
		when(service.uploadTip(1L, expected)).thenReturn(uploaded);
		authenticate();

		var response = tipController.uploadTipMultipart(AUTHORIZATION, 1L, Map.of("category", "COURSE", "title", "title", "content", "content", "gpsVisible", "true", "courseAddress", "Seoul Forest"), null, null, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isSameAs(uploaded);
	}

	@Test
	void missingTipApis_supportAndroidContracts() {
		var update = new com.neostride.server.community.dto.TipUploadRequest("COURSE", "title", "body", true, "route.png", List.of("tip.png"));
		var tip = new com.neostride.server.community.dto.TipUploadResponse(7L, "neo", "photo.png", true, "COURSE", "title", "body", true, "route.png", List.of("tip.png"), 1, 1, "2026-05-11T00:00:00");
		var detail = new com.neostride.server.community.dto.TipDetailResponse(7L, 1L, "neo", "photo.png", true, "GOLD", "COURSE", "title", "body", true, "route.png", null, List.of("tip.png"), 1, 1, true, false, true, "2026-05-11T00:00:00", List.of());
		var commentRequest = new com.neostride.server.community.dto.CommentRequest("hello");
		var comment = new com.neostride.server.community.dto.CommentResponse(8L, 1L, "neo", "photo.png", "hello", "2026-05-11T00:00:00", true, "GOLD", true);
		authenticate();
		when(service.getMyTips(1L)).thenReturn(List.of(tip));
		when(service.getUserTips(1L, 2L)).thenReturn(List.of(tip));
		when(service.getTipDetail(1L, 7L)).thenReturn(detail);
		when(service.toggleTipLike(1L, 7L)).thenReturn(Map.of("liked", "true"));
		when(service.toggleTipBookmark(1L, 7L)).thenReturn(Map.of("bookmarked", "true"));
		when(service.createTipComment(1L, 7L, commentRequest)).thenReturn(comment);
		when(service.updateTip(1L, 7L, update)).thenReturn(tip);
		when(service.updateTipComment(1L, 7L, 8L, commentRequest)).thenReturn(comment);

		assertThat(tipController.getMyTips(AUTHORIZATION, 1L).getBody()).containsExactly(tip);
		assertThat(tipController.getRunnerTips(AUTHORIZATION, 1L, 2L).getBody()).containsExactly(tip);
		assertThat(tipController.getTipDetail(AUTHORIZATION, 1L, 7L).getBody()).isSameAs(detail);
		assertThat(tipController.toggleTipLike(AUTHORIZATION, 1L, 7L).getBody()).containsEntry("liked", "true");
		assertThat(tipController.toggleTipBookmark(AUTHORIZATION, 1L, 7L).getBody()).containsEntry("bookmarked", "true");
		assertThat(tipController.createTipComment(AUTHORIZATION, 1L, 7L, commentRequest).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(tipController.updateTip(AUTHORIZATION, 1L, 7L, update).getBody()).isSameAs(tip);
		assertThat(tipController.updateTipComment(AUTHORIZATION, 1L, 7L, 8L, commentRequest).getBody()).isSameAs(comment);
		assertThat(tipController.deleteTipComment(AUTHORIZATION, 1L, 7L, 8L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(tipController.deleteTip(AUTHORIZATION, 1L, 7L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	private void authenticate() {
		when(authenticatedUserService.requireUserId(AUTHORIZATION)).thenReturn(1L);
	}
}
