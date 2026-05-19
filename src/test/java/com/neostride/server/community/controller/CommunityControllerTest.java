package com.neostride.server.community.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.community.dto.BadgeDetailResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendRequest;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.community.dto.SearchUserResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.community.service.CommunityService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityControllerTest {

	private static final String AUTHORIZATION = "Bearer access-token";

	private final CommunityService service = mock(CommunityService.class);
	private final AuthenticatedUserService authenticatedUserService = mock(AuthenticatedUserService.class);
	private final CommunityController controller = new CommunityController(service, authenticatedUserService);

	@Test
	void getUserProfile_returnsAuthenticatedUserProfile() {
		UserProfileResponse body = new UserProfileResponse("neo", "photo.png", "running", 2, 3, 4, 5, 6, 7);
		authenticate();
		when(service.getUserProfile(1L)).thenReturn(body);

		var response = controller.getUserProfile(AUTHORIZATION, 1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(body);
	}

	@Test
	void updateStatusMessage_returnsNoContent() {
		authenticate();

		var response = controller.updateStatusMessage(AUTHORIZATION, 1L, Map.of("status_message", "ready"));

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

		assertThat(controller.getMyFeeds(AUTHORIZATION, 1L).getBody()).isSameAs(body);
		assertThat(controller.getTaggedFeeds(AUTHORIZATION, 1L).getBody()).isSameAs(body);
		assertThat(controller.getCommentedFeeds(AUTHORIZATION, 1L).getBody()).isSameAs(body);
		assertThat(controller.getLikedFeeds(AUTHORIZATION, 1L).getBody()).isSameAs(body);
		assertThat(controller.getBookmarkedFeeds(AUTHORIZATION, 1L).getBody()).isSameAs(body);
	}

	@Test
	void getBadgeDetail_returnsOkBadge() {
		BadgeDetailResponse body = new BadgeDetailResponse("GOLD", 11L, new BigDecimal("10.0"), "5'30\"", "2026-05-11T00:00:00");
		authenticate();
		when(service.getBadgeDetail(1L)).thenReturn(body);

		var response = controller.getBadgeDetail(AUTHORIZATION, 1L);

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

		assertThat(controller.getCommunityFriends(AUTHORIZATION, 1L, "friends").getBody()).isSameAs(friends);
		assertThat(controller.getLegacyRelationships(AUTHORIZATION, 1L, "friends").getBody()).isSameAs(friends);
		assertThat(controller.updateCommunityRelationship(AUTHORIZATION, 1L, request).getBody()).containsEntry("status", "success");
		assertThat(controller.updateLegacyRelationship(AUTHORIZATION, 1L, request).getBody()).containsEntry("status", "success");
	}

	@Test
	void feedApis_uploadAndListFeeds() {
		FeedUploadRequest request = new FeedUploadRequest("title", "content", "PUBLIC", true, "route.png", List.of(2L), List.of("image.png"), new BigDecimal("3.2"), "20:00", "6'15\"", 1);
		FeedUploadResponse uploaded = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", "content", 1, 0, 0, "3.20 km", "20:00", "6'15\"", true, "route.png", List.of("image.png"));
		when(service.uploadFeed(1L, request)).thenReturn(uploaded);
		when(service.getFeedList()).thenReturn(List.of(uploaded));
		authenticate();

		assertThat(controller.uploadFeed(AUTHORIZATION, 1L, request).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(controller.getFeedList().getBody()).containsExactly(uploaded);
	}

	@Test
	void accountApis_supportCurrentUserAccountNicknameAndDeletion() {
		var account = new com.neostride.server.community.dto.AccountInfoResponse("runner@example.com", "neo", "photo.png");
		authenticate();
		when(service.getAccountInfo(1L)).thenReturn(account);

		assertThat(controller.getAccountInfo(AUTHORIZATION, 1L).getBody()).isSameAs(account);
		assertThat(controller.updateNickname(AUTHORIZATION, 1L, Map.of("nickname", "neo2")).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(controller.deleteAccount(AUTHORIZATION, 1L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void runnerPageApis_returnOtherUserProfileBadgeFeedsAndFriends() {
		UserProfileResponse profile = new UserProfileResponse("runner", "photo.png", "ready", 2, 3, 0, 0, 0, 0);
		BadgeDetailResponse badge = new BadgeDetailResponse("GOLD", 11L, new BigDecimal("10.0"), "5", "2026-05-11T00:00:00");
		List<CommunityContentResponse> feeds = List.of(new CommunityContentResponse(10L, "text", new BigDecimal("3.2"), 1200, 6, "2026-05-11T00:00:00"));
		List<FriendResponse> friends = List.of(new FriendResponse(3L, "friend", "SILVER", 4, "friend.png", "friends"));
		when(service.getUserProfile(2L)).thenReturn(profile);
		when(service.getBadgeDetail(2L)).thenReturn(badge);
		when(service.getUserFeeds(2L)).thenReturn(feeds);
		when(service.getUserFriendList(2L)).thenReturn(friends);

		assertThat(controller.getRunnerProfile(2L).getBody()).isSameAs(profile);
		assertThat(controller.getUserBadgeDetail(2L).getBody()).isSameAs(badge);
		assertThat(controller.getRunnerFeeds(2L).getBody()).isSameAs(feeds);
		assertThat(controller.getUserFriendList(2L).getBody()).isSameAs(friends);
	}

	@Test
	void bookmarkAndTipApis_supportAndroidContracts() {
		var bookmark = Map.of("status", "success", "bookmarked", "true");
		var tipRequest = new com.neostride.server.community.dto.TipUploadRequest("COURSE", "title", "content", true, "route.png", List.of("tip.png"));
		var tip = new com.neostride.server.community.dto.TipUploadResponse(7L, "neo", "photo.png", true, "COURSE", "title", "content", true, "route.png", List.of("tip.png"), 1, 2, "2026-05-11T00:00:00");
		var tipList = new com.neostride.server.community.dto.TipListResponse(List.of(tip));
		when(service.toggleBookmark(1L, 10L)).thenReturn(bookmark);
		when(service.uploadTip(1L, tipRequest)).thenReturn(tip);
		when(service.getTips()).thenReturn(tipList);
		authenticate();

		assertThat(controller.toggleBookmark(AUTHORIZATION, 1L, 10L).getBody()).isSameAs(bookmark);
		assertThat(controller.uploadTip(AUTHORIZATION, 1L, tipRequest).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(controller.uploadTip(AUTHORIZATION, 1L, tipRequest).getBody()).isSameAs(tip);
		assertThat(controller.getTips().getBody()).isSameAs(tipList);
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
		when(service.getTopProfiles(0, 10)).thenReturn(List.of(user));
		when(service.getMyFriends(1L)).thenReturn(List.of(user));
		authenticate();

		assertThat(controller.searchFeeds("run", 0, 10).getBody()).containsExactly(feed);
		assertThat(controller.searchTips("pace", "ALL", 0, 10).getBody()).containsExactly(tip);
		assertThat(controller.searchProfiles("neo", 0, 10).getBody()).containsExactly(user);
		assertThat(controller.searchFriends(AUTHORIZATION, 1L, "neo").getBody()).containsExactly(user);
		assertThat(controller.getTopProfiles(0, 10).getBody()).containsExactly(user);
		assertThat(controller.getMyFriends(AUTHORIZATION, 1L).getBody()).containsExactly(user);
	}

	private void authenticate() {
		when(authenticatedUserService.requireUserId(AUTHORIZATION)).thenReturn(1L);
	}
}
