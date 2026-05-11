package com.neostride.server.community.controller;

import com.neostride.server.community.dto.BadgeDetailResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendRequest;
import com.neostride.server.community.dto.FriendResponse;
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

	private final CommunityService service = mock(CommunityService.class);
	private final CommunityController controller = new CommunityController(service);

	@Test
	void getUserProfile_returnsAuthenticatedUserProfile() {
		UserProfileResponse body = new UserProfileResponse("neo", "photo.png", "running", 2, 3, 4, 5, 6, 7);
		when(service.getUserProfile(1L)).thenReturn(body);

		var response = controller.getUserProfile(1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(body);
	}

	@Test
	void updateStatusMessage_returnsNoContent() {
		var response = controller.updateStatusMessage(1L, Map.of("status_message", "ready"));

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

		assertThat(controller.getMyFeeds(1L).getBody()).isSameAs(body);
		assertThat(controller.getTaggedFeeds(1L).getBody()).isSameAs(body);
		assertThat(controller.getCommentedFeeds(1L).getBody()).isSameAs(body);
		assertThat(controller.getLikedFeeds(1L).getBody()).isSameAs(body);
		assertThat(controller.getBookmarkedFeeds(1L).getBody()).isSameAs(body);
	}

	@Test
	void getBadgeDetail_returnsOkBadge() {
		BadgeDetailResponse body = new BadgeDetailResponse("GOLD", 11L, new BigDecimal("10.0"), "5'30\"", "2026-05-11T00:00:00");
		when(service.getBadgeDetail(1L)).thenReturn(body);

		var response = controller.getBadgeDetail(1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(body);
	}

	@Test
	void friendApis_supportCommunityAndLegacyRelationshipPaths() {
		List<FriendResponse> friends = List.of(new FriendResponse(2L, "runner", "SILVER", 3, "photo.png", "friends"));
		FriendRequest request = new FriendRequest(2L, "accept");
		when(service.getFriendList(1L, "friends")).thenReturn(friends);
		when(service.updateRelationship(1L, request)).thenReturn(Map.of("status", "success"));

		assertThat(controller.getCommunityFriends(1L, "friends").getBody()).isSameAs(friends);
		assertThat(controller.getLegacyRelationships(1L, "friends").getBody()).isSameAs(friends);
		assertThat(controller.updateCommunityRelationship(1L, request).getBody()).containsEntry("status", "success");
		assertThat(controller.updateLegacyRelationship(1L, request).getBody()).containsEntry("status", "success");
	}

	@Test
	void feedApis_uploadAndListFeeds() {
		FeedUploadRequest request = new FeedUploadRequest("title", "content", "PUBLIC", true, "route.png", List.of(2L), List.of("image.png"), new BigDecimal("3.2"), "20:00", "6'15\"", 1);
		FeedUploadResponse uploaded = new FeedUploadResponse(99L, null, "neo", "2026-05-11T00:00:00", "title", "content", 1, 0, 0, "3.20 km", "20:00", "6'15\"", true, "route.png", List.of("image.png"));
		when(service.uploadFeed(1L, request)).thenReturn(uploaded);
		when(service.getFeedList(1L)).thenReturn(List.of(uploaded));

		assertThat(controller.uploadFeed(1L, request).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(controller.getFeedList(1L).getBody()).containsExactly(uploaded);
	}
}
