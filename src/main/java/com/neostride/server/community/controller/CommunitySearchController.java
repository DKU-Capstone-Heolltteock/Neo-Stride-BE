package com.neostride.server.community.controller;

import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.SearchUserResponse;
import com.neostride.server.community.dto.TipUploadResponse;
import com.neostride.server.community.service.CommunityService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommunitySearchController {
	private final CommunityService service;
	private final CommunityUserContext userContext;

	public CommunitySearchController(CommunityService service, CommunityUserContext userContext) {
		this.service = service;
		this.userContext = userContext;
	}

	@GetMapping("/api/community/search/feeds")
	public ResponseEntity<List<FeedUploadResponse>> searchFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size
	) {
		return ResponseEntity.ok(service.searchFeeds(userContext.optionalUserId(authorization, headerUserId),
				keyword, page, size));
	}

	@GetMapping("/api/community/search/tips")
	public ResponseEntity<List<TipUploadResponse>> searchTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "category", required = false, defaultValue = "ALL") String category,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size
	) {
		return ResponseEntity.ok(service.searchTips(userContext.optionalUserId(authorization, headerUserId),
				keyword, category, page, size));
	}

	@GetMapping("/api/community/search/profiles")
	public ResponseEntity<List<SearchUserResponse>> searchProfiles(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size
	) {
		return ResponseEntity.ok(service.searchProfiles(userContext.optionalUserId(authorization, headerUserId),
				keyword, page, size));
	}

	@GetMapping("/api/community/search/friends")
	public ResponseEntity<List<SearchUserResponse>> searchFriends(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(value = "keyword", required = false) String keyword
	) {
		return ResponseEntity.ok(asFriends(service.searchFriends(userContext.authenticatedUserId(authorization, headerUserId),
				keyword)));
	}

	@GetMapping("/api/community/search/top-profiles")
	public ResponseEntity<List<SearchUserResponse>> getTopProfiles(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size
	) {
		return ResponseEntity.ok(service.getTopProfiles(userContext.optionalUserId(authorization, headerUserId),
				page, size));
	}

	@GetMapping("/api/community/search/my-friends")
	public ResponseEntity<List<SearchUserResponse>> getMyFriends(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(asFriends(service.getMyFriends(userContext.authenticatedUserId(authorization, headerUserId))));
	}

	public ResponseEntity<List<FeedUploadResponse>> searchFeeds(String keyword, int page, int size) {
		return ResponseEntity.ok(service.searchFeeds(keyword, page, size));
	}

	public ResponseEntity<List<TipUploadResponse>> searchTips(String keyword, String category, int page, int size) {
		return ResponseEntity.ok(service.searchTips(keyword, category, page, size));
	}

	public ResponseEntity<List<SearchUserResponse>> searchProfiles(String keyword, int page, int size) {
		return ResponseEntity.ok(service.searchProfiles(keyword, page, size));
	}

	public ResponseEntity<List<SearchUserResponse>> getTopProfiles(int page, int size) {
		return ResponseEntity.ok(service.getTopProfiles(page, size));
	}

	private static List<SearchUserResponse> asFriends(List<SearchUserResponse> users) {
		if (users == null || users.isEmpty()) {
			return List.of();
		}
		return users.stream().map(CommunitySearchController::asFriend).toList();
	}

	private static SearchUserResponse asFriend(SearchUserResponse response) {
		if (response == null) {
			return null;
		}
		return new SearchUserResponse(response.userId(), response.nickname(), response.profileImageUrl(),
				response.statusMessage(), response.friendCount(), response.badgeTier(), "friends");
	}
}
