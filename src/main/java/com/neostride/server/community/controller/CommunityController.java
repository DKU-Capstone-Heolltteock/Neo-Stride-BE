package com.neostride.server.community.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.community.dto.BadgeDetailResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendRequest;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.community.dto.AccountInfoResponse;
import com.neostride.server.community.dto.TipListResponse;
import com.neostride.server.community.dto.TipUploadRequest;
import com.neostride.server.community.dto.TipUploadResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.community.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

	public CommunityController(CommunityService service, AuthenticatedUserService authenticatedUserService) {
		this.service = service;
		this.authenticatedUserService = authenticatedUserService;
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
		String storedValue = profileImageUrl;
		if ((storedValue == null || storedValue.isBlank()) && image != null && !image.isEmpty()) {
			storedValue = image.getOriginalFilename();
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
	@GetMapping("/feeds")
	public ResponseEntity<List<FeedUploadResponse>> getFeedList() { return ResponseEntity.ok(service.getFeedList()); }
	@PostMapping("/api/tips")
	public ResponseEntity<TipUploadResponse> uploadTip(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody TipUploadRequest request
	) { return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadTip(authenticatedUserId(authorization, headerUserId), request)); }
	@GetMapping("/api/tips")
	public ResponseEntity<TipListResponse> getTips() { return ResponseEntity.ok(service.getTips()); }

	private long authenticatedUserId(String authorization, Long headerUserId) {
		long authenticatedUserId = authenticatedUserService.requireUserId(authorization);
		authenticatedUserService.requireSameUserIfPresent(authenticatedUserId, headerUserId, "X-User-Id");
		return authenticatedUserId;
	}
}
