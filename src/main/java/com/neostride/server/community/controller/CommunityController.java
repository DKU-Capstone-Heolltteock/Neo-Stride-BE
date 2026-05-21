package com.neostride.server.community.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.community.dto.*;
import com.neostride.server.community.service.CommunityService;
import com.neostride.server.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Community", description = "커뮤니티/마이페이지/친구/배지/피드 API")
@RestController
public class CommunityController {
	private final CommunityService service;
	private final AuthenticatedUserService authenticatedUserService;
	private final StorageService storageService;

	public CommunityController(CommunityService service, AuthenticatedUserService authenticatedUserService, StorageService storageService) {
		this.service = service;
		this.authenticatedUserService = authenticatedUserService;
		this.storageService = storageService;
	}

	@Operation(summary = "내 프로필 조회")
	@GetMapping("/users/me/profile")
	public ResponseEntity<UserProfileResponse> getUserProfile(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getUserProfile(authenticatedUserId(authorization, headerUserId))); }
	@GetMapping("/users/{userId}/profile")
	public ResponseEntity<UserProfileResponse> getRunnerProfile(@org.springframework.web.bind.annotation.PathVariable long userId) { return ResponseEntity.ok(service.getUserProfile(userId)); }
	@GetMapping("/users/me/account")
	public ResponseEntity<AccountInfoResponse> getAccountInfo(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getAccountInfo(authenticatedUserId(authorization, headerUserId))); }

	@Operation(summary = "내 상태 메시지 변경")
	@PatchMapping("/users/me/status")
	public ResponseEntity<Void> updateStatusMessage(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody Map<String, String> body
	) { service.updateStatusMessage(authenticatedUserId(authorization, headerUserId), body); return ResponseEntity.noContent().build(); }
	@PatchMapping("/users/me/nickname")
	public ResponseEntity<Void> updateNickname(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody Map<String, String> body
	) { service.updateNickname(authenticatedUserId(authorization, headerUserId), body); return ResponseEntity.noContent().build(); }
	@DeleteMapping("/users/me")
	public ResponseEntity<Void> deleteAccount(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { service.deleteAccount(authenticatedUserId(authorization, headerUserId)); return ResponseEntity.noContent().build(); }

	@Operation(summary = "내 프로필 이미지 변경")
	@PatchMapping("/users/me/profile-image")
	public ResponseEntity<Void> updateProfileImage(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(value = "profile_image_url", required = false) String profileImageUrl,
			@RequestPart(value = "image", required = false) MultipartFile image
	) {
		String storedValue = normalizedNonBlank(profileImageUrl);
		if (image != null) {
			storedValue = storageService.storeImage(image, "profile");
		}
		if (storedValue == null) {
			throw new IllegalArgumentException("profile_image_url 또는 image 중 하나는 필요합니다.");
		}
		service.updateProfileImage(authenticatedUserId(authorization, headerUserId), storedValue);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/community/contents/me")
	public ResponseEntity<List<CommunityContentResponse>> getMyFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getMyFeeds(authenticatedUserId(authorization, headerUserId))); }
	@GetMapping("/community/contents/tagged")
	public ResponseEntity<List<CommunityContentResponse>> getTaggedFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getTaggedFeeds(authenticatedUserId(authorization, headerUserId))); }
	@GetMapping("/community/contents/comments")
	public ResponseEntity<List<CommunityContentResponse>> getCommentedFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getCommentedFeeds(authenticatedUserId(authorization, headerUserId))); }
	@GetMapping("/community/contents/likes")
	public ResponseEntity<List<CommunityContentResponse>> getLikedFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getLikedFeeds(authenticatedUserId(authorization, headerUserId))); }
	@GetMapping("/community/contents/bookmarks")
	public ResponseEntity<List<CommunityContentResponse>> getBookmarkedFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getBookmarkedFeeds(authenticatedUserId(authorization, headerUserId))); }
	@GetMapping("/community/contents/user/{userId}")
	public ResponseEntity<List<CommunityContentResponse>> getRunnerFeeds(@org.springframework.web.bind.annotation.PathVariable long userId) { return ResponseEntity.ok(service.getUserFeeds(userId)); }
	@PostMapping("/community/bookmark/{contentId}")
	public ResponseEntity<Map<String, String>> toggleBookmark(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long contentId
	) { return ResponseEntity.ok(service.toggleBookmark(authenticatedUserId(authorization, headerUserId), contentId)); }

	@GetMapping("/users/me/badge")
	public ResponseEntity<BadgeDetailResponse> getBadgeDetail(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getBadgeDetail(authenticatedUserId(authorization, headerUserId))); }
	@GetMapping("/users/{userId}/badge")
	public ResponseEntity<BadgeDetailResponse> getUserBadgeDetail(@org.springframework.web.bind.annotation.PathVariable long userId) { return ResponseEntity.ok(service.getBadgeDetail(userId)); }

	@GetMapping("/community/friends")
	public ResponseEntity<List<FriendResponse>> getCommunityFriends(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam String status
	) { return ResponseEntity.ok(service.getFriendList(authenticatedUserId(authorization, headerUserId), status)); }
	@GetMapping("/community/friends/user/{userId}")
	public ResponseEntity<List<FriendResponse>> getUserFriendList(@org.springframework.web.bind.annotation.PathVariable long userId) { return ResponseEntity.ok(service.getUserFriendList(userId)); }
	@PostMapping("/community/friends/action")
	public ResponseEntity<Map<String, String>> updateCommunityRelationship(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody FriendRequest request
	) { return ResponseEntity.ok(service.updateRelationship(authenticatedUserId(authorization, headerUserId), request)); }
	@GetMapping("/api/relationships")
	public ResponseEntity<List<FriendResponse>> getLegacyRelationships(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam String status
	) { return ResponseEntity.ok(service.getFriendList(authenticatedUserId(authorization, headerUserId), status)); }
	@PostMapping("/api/relationships/action")
	public ResponseEntity<Map<String, String>> updateLegacyRelationship(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody FriendRequest request
	) { return ResponseEntity.ok(service.updateRelationship(authenticatedUserId(authorization, headerUserId), request)); }

	@PostMapping("/feeds")
	public ResponseEntity<FeedUploadResponse> uploadFeed(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody FeedUploadRequest request
	) { return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadFeed(authenticatedUserId(authorization, headerUserId), request)); }
	@GetMapping("/api/community/friends")
	public ResponseEntity<List<FriendResponse>> getApiCommunityFriends(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam String status
	) { return ResponseEntity.ok(service.getFriendList(authenticatedUserId(authorization, headerUserId), status)); }
	@GetMapping("/api/community/feeds/{feedId}")
	public ResponseEntity<FeedDetailResponse> getFeedDetail(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long feedId
	) { return ResponseEntity.ok(service.getFeedDetail(authenticatedUserId(authorization, headerUserId), feedId)); }
	@PostMapping("/api/community/feeds/{feedId}/likes")
	public ResponseEntity<Map<String, String>> toggleFeedLike(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long feedId
	) { return ResponseEntity.ok(service.toggleFeedLike(authenticatedUserId(authorization, headerUserId), feedId)); }
	@PostMapping("/api/community/feeds/{feedId}/bookmarks")
	public ResponseEntity<Map<String, String>> toggleFeedBookmark(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long feedId
	) { return ResponseEntity.ok(service.toggleFeedBookmark(authenticatedUserId(authorization, headerUserId), feedId)); }
	@PostMapping("/api/community/feeds/{feedId}/comments")
	public ResponseEntity<CommentResponse> createFeedComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long feedId,
			@RequestBody CommentRequest request
	) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createFeedComment(authenticatedUserId(authorization, headerUserId), feedId, request)); }
	@DeleteMapping("/api/community/feeds/{feedId}")
	public ResponseEntity<Void> deleteFeed(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long feedId
	) { service.deleteFeed(authenticatedUserId(authorization, headerUserId), feedId); return ResponseEntity.noContent().build(); }
	@PutMapping("/api/community/feeds/{feedId}")
	public ResponseEntity<FeedUploadResponse> updateFeed(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long feedId,
			@RequestBody FeedUploadRequest request
	) { return ResponseEntity.ok(service.updateFeed(authenticatedUserId(authorization, headerUserId), feedId, request)); }
	@PutMapping("/api/community/feeds/{feedId}/comments/{commentId}")
	public ResponseEntity<CommentResponse> updateFeedComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long feedId,
			@org.springframework.web.bind.annotation.PathVariable long commentId,
			@RequestBody CommentRequest request
	) { return ResponseEntity.ok(service.updateFeedComment(authenticatedUserId(authorization, headerUserId), feedId, commentId, request)); }
	@DeleteMapping("/api/community/feeds/{feedId}/comments/{commentId}")
	public ResponseEntity<Void> deleteFeedComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long feedId,
			@org.springframework.web.bind.annotation.PathVariable long commentId
	) { service.deleteFeedComment(authenticatedUserId(authorization, headerUserId), feedId, commentId); return ResponseEntity.noContent().build(); }
	@GetMapping("/api/community/feeds/{feedId}/tagged-users")
	public ResponseEntity<List<FriendResponse>> getTaggedUsers(@org.springframework.web.bind.annotation.PathVariable long feedId) { return ResponseEntity.ok(service.getTaggedUsers(feedId)); }
	@PostMapping(value = "/api/community/feeds", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<FeedUploadResponse> uploadCommunityFeedJson(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody FeedUploadRequest request
	) { return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadFeed(authenticatedUserId(authorization, headerUserId), request)); }
	@PostMapping(value = "/api/community/feeds", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<FeedUploadResponse> uploadFeedMultipart(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam Map<String, String> fields,
			@RequestPart(value = "images", required = false) List<MultipartFile> images,
			@RequestPart(value = "route_image", required = false) MultipartFile routeImage,
			@RequestPart(value = "routeMapImage", required = false) MultipartFile routeMapImage
	) {
		FeedUploadRequest request = new FeedUploadRequest(
				fields.get("title"), fields.get("content"), fields.get("privacy"), bool(fields.get("mapVisible")),
				storedRouteUri(routeImage != null ? routeImage : routeMapImage), parseLongList(fields.get("taggedUserIds")), storedImageUris(images),
				decimal(fields.get("distance")), fields.get("runningTime"), fields.get("pace"), parseInt(fields.get("tagCount"))
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadFeed(authenticatedUserId(authorization, headerUserId), request));
	}
	@GetMapping("/feeds")
	public ResponseEntity<List<FeedUploadResponse>> getFeedList() { return ResponseEntity.ok(service.getFeedList()); }
	@GetMapping("/api/community/feeds")
	public ResponseEntity<List<FeedUploadResponse>> getCommunityFeedList() { return ResponseEntity.ok(service.getFeedList()); }
	@PostMapping("/api/tips")
	public ResponseEntity<TipUploadResponse> uploadTip(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody TipUploadRequest request
	) { return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadTip(authenticatedUserId(authorization, headerUserId), request)); }
	@GetMapping("/api/tips")
	public ResponseEntity<TipListResponse> getTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getTips(optionalUserId(authorization, headerUserId))); }
	@PostMapping(value = "/api/community/tips", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TipUploadResponse> uploadCommunityTipJson(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody TipUploadRequest request
	) { return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadTip(authenticatedUserId(authorization, headerUserId), request)); }
	@PostMapping(value = "/api/community/tips", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<TipUploadResponse> uploadTipMultipart(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam Map<String, String> fields,
			@RequestPart(value = "images", required = false) List<MultipartFile> images,
			@RequestPart(value = "route_image", required = false) MultipartFile routeImage,
			@RequestPart(value = "routeMapImage", required = false) MultipartFile routeMapImage
	) {
		TipUploadRequest request = new TipUploadRequest(fields.get("category"), fields.get("title"), fields.get("content"), bool(fields.get("gpsVisible")), storedRouteUri(routeImage != null ? routeImage : routeMapImage), storedImageUris(images));
		return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadTip(authenticatedUserId(authorization, headerUserId), request));
	}
	@GetMapping("/api/community/tips")
	public ResponseEntity<List<TipUploadResponse>> getCommunityTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getTips(optionalUserId(authorization, headerUserId)).tips()); }
	@GetMapping("/api/community/tips/me")
	public ResponseEntity<List<TipUploadResponse>> getMyTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getMyTips(authenticatedUserId(authorization, headerUserId))); }
	@GetMapping("/community/tips/user/{userId}")
	public ResponseEntity<List<TipUploadResponse>> getRunnerTips(@org.springframework.web.bind.annotation.PathVariable long userId) { return ResponseEntity.ok(service.getUserTips(userId)); }
	@GetMapping("/api/community/tips/{tipId}")
	public ResponseEntity<TipDetailResponse> getTipDetail(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long tipId
	) { return ResponseEntity.ok(service.getTipDetail(authenticatedUserId(authorization, headerUserId), tipId)); }
	@PostMapping("/api/community/tips/{tipId}/likes")
	public ResponseEntity<Map<String, String>> toggleTipLike(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long tipId
	) { return ResponseEntity.ok(service.toggleTipLike(authenticatedUserId(authorization, headerUserId), tipId)); }
	@PostMapping("/api/community/tips/{tipId}/bookmarks")
	public ResponseEntity<Map<String, String>> toggleTipBookmark(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long tipId
	) { return ResponseEntity.ok(service.toggleTipBookmark(authenticatedUserId(authorization, headerUserId), tipId)); }
	@PostMapping("/api/community/tips/{tipId}/comments")
	public ResponseEntity<CommentResponse> createTipComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long tipId,
			@RequestBody CommentRequest request
	) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createTipComment(authenticatedUserId(authorization, headerUserId), tipId, request)); }
	@DeleteMapping("/api/community/tips/{tipId}")
	public ResponseEntity<Void> deleteTip(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long tipId
	) { service.deleteTip(authenticatedUserId(authorization, headerUserId), tipId); return ResponseEntity.noContent().build(); }
	@PutMapping("/api/community/tips/{tipId}")
	public ResponseEntity<TipUploadResponse> updateTip(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long tipId,
			@RequestBody TipUploadRequest request
	) { return ResponseEntity.ok(service.updateTip(authenticatedUserId(authorization, headerUserId), tipId, request)); }
	@PutMapping("/api/community/tips/{tipId}/comments/{commentId}")
	public ResponseEntity<CommentResponse> updateTipComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long tipId,
			@org.springframework.web.bind.annotation.PathVariable long commentId,
			@RequestBody CommentRequest request
	) { return ResponseEntity.ok(service.updateTipComment(authenticatedUserId(authorization, headerUserId), tipId, commentId, request)); }
	@DeleteMapping("/api/community/tips/{tipId}/comments/{commentId}")
	public ResponseEntity<Void> deleteTipComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@org.springframework.web.bind.annotation.PathVariable long tipId,
			@org.springframework.web.bind.annotation.PathVariable long commentId
	) { service.deleteTipComment(authenticatedUserId(authorization, headerUserId), tipId, commentId); return ResponseEntity.noContent().build(); }

	@GetMapping("/api/community/search/feeds")
	public ResponseEntity<List<FeedUploadResponse>> searchFeeds(
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size
	) { return ResponseEntity.ok(service.searchFeeds(keyword, page, size)); }

	@GetMapping("/api/community/search/tips")
	public ResponseEntity<List<TipUploadResponse>> searchTips(
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "category", required = false, defaultValue = "ALL") String category,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size
	) { return ResponseEntity.ok(service.searchTips(keyword, category, page, size)); }

	@GetMapping("/api/community/search/profiles")
	public ResponseEntity<List<SearchUserResponse>> searchProfiles(
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size
	) { return ResponseEntity.ok(service.searchProfiles(keyword, page, size)); }

	@GetMapping("/api/community/search/friends")
	public ResponseEntity<List<SearchUserResponse>> searchFriends(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(value = "keyword", required = false) String keyword
	) { return ResponseEntity.ok(service.searchFriends(authenticatedUserId(authorization, headerUserId), keyword)); }

	@GetMapping("/api/community/search/top-profiles")
	public ResponseEntity<List<SearchUserResponse>> getTopProfiles(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size
	) { return ResponseEntity.ok(service.getTopProfiles(page, size)); }

	@GetMapping("/api/community/search/my-friends")
	public ResponseEntity<List<SearchUserResponse>> getMyFriends(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) { return ResponseEntity.ok(service.getMyFriends(authenticatedUserId(authorization, headerUserId))); }

	private static boolean bool(String value) { return Boolean.parseBoolean(value); }
	private static int parseInt(String value) { return value == null || value.isBlank() ? 0 : Integer.parseInt(value); }
	private static BigDecimal decimal(String value) { return value == null || value.isBlank() ? null : new BigDecimal(value); }
	private static List<Long> parseLongList(String value) {
		if (value == null || value.isBlank()) return List.of();
		String normalized = value.trim();
		if (normalized.startsWith("[") && normalized.endsWith("]")) {
			normalized = normalized.substring(1, normalized.length() - 1);
		}
		List<Long> ids = new ArrayList<>();
		for (String part : normalized.split(",")) {
			String token = part.trim();
			if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
				token = token.substring(1, token.length() - 1).trim();
			}
			if (!token.isBlank()) ids.add(Long.parseLong(token));
		}
		return ids;
	}
	private List<String> storedImageUris(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) return List.of();
		if (files.size() > 1) {
			throw new IllegalArgumentException("현재 피드/팁 업로드는 단일 이미지만 지원합니다.");
		}
		MultipartFile file = files.get(0);
		if (file == null) return List.of();
		return List.of(storageService.storeImage(file, "community"));
	}

	private String storedRouteUri(MultipartFile file) {
		if (file == null) return null;
		return storageService.storeImage(file, "routes");
	}

	private static String normalizedNonBlank(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim();
	}

	private long authenticatedUserId(String authorization, Long headerUserId) {
		long authenticatedUserId = authenticatedUserService.requireUserId(authorization);
		authenticatedUserService.requireSameUserIfPresent(authenticatedUserId, headerUserId, "X-User-Id");
		return authenticatedUserId;
	}

	private Long optionalUserId(String authorization, Long headerUserId) {
		if (authorization != null && !authorization.isBlank()) {
			return authenticatedUserId(authorization, headerUserId);
		}
		return headerUserId != null && headerUserId > 0 ? headerUserId : null;
	}
}
