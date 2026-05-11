package com.neostride.server.community.controller;

import com.neostride.server.community.dto.BadgeDetailResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendRequest;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.community.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
	public CommunityController(CommunityService service) { this.service = service; }

	@Operation(summary = "내 프로필 조회")
	@GetMapping("/users/me/profile")
	public ResponseEntity<UserProfileResponse> getUserProfile(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId) { return ResponseEntity.ok(service.getUserProfile(userId)); }

	@Operation(summary = "내 상태 메시지 변경")
	@PatchMapping("/users/me/status")
	public ResponseEntity<Void> updateStatusMessage(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId, @RequestBody Map<String, String> body) { service.updateStatusMessage(userId, body); return ResponseEntity.noContent().build(); }

	@Operation(summary = "내 프로필 이미지 변경")
	@PatchMapping("/users/me/profile-image")
	public ResponseEntity<Void> updateProfileImage(
			@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId,
			@RequestParam(value = "profile_image_url", required = false) String profileImageUrl,
			@RequestPart(value = "image", required = false) MultipartFile image
	) {
		String storedValue = profileImageUrl;
		if ((storedValue == null || storedValue.isBlank()) && image != null && !image.isEmpty()) {
			storedValue = image.getOriginalFilename();
		}
		service.updateProfileImage(userId, storedValue);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/community/contents/me")
	public ResponseEntity<List<CommunityContentResponse>> getMyFeeds(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId) { return ResponseEntity.ok(service.getMyFeeds(userId)); }
	@GetMapping("/community/contents/tagged")
	public ResponseEntity<List<CommunityContentResponse>> getTaggedFeeds(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId) { return ResponseEntity.ok(service.getTaggedFeeds(userId)); }
	@GetMapping("/community/contents/comments")
	public ResponseEntity<List<CommunityContentResponse>> getCommentedFeeds(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId) { return ResponseEntity.ok(service.getCommentedFeeds(userId)); }
	@GetMapping("/community/contents/likes")
	public ResponseEntity<List<CommunityContentResponse>> getLikedFeeds(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId) { return ResponseEntity.ok(service.getLikedFeeds(userId)); }
	@GetMapping("/community/contents/bookmarks")
	public ResponseEntity<List<CommunityContentResponse>> getBookmarkedFeeds(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId) { return ResponseEntity.ok(service.getBookmarkedFeeds(userId)); }

	@GetMapping("/users/me/badge")
	public ResponseEntity<BadgeDetailResponse> getBadgeDetail(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId) { return ResponseEntity.ok(service.getBadgeDetail(userId)); }

	@GetMapping("/community/friends")
	public ResponseEntity<List<FriendResponse>> getCommunityFriends(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId, @RequestParam String status) { return ResponseEntity.ok(service.getFriendList(userId, status)); }
	@PostMapping("/community/friends/action")
	public ResponseEntity<Map<String, String>> updateCommunityRelationship(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId, @RequestBody FriendRequest request) { return ResponseEntity.ok(service.updateRelationship(userId, request)); }
	@GetMapping("/api/relationships")
	public ResponseEntity<List<FriendResponse>> getLegacyRelationships(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId, @RequestParam String status) { return ResponseEntity.ok(service.getFriendList(userId, status)); }
	@PostMapping("/api/relationships/action")
	public ResponseEntity<Map<String, String>> updateLegacyRelationship(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId, @RequestBody FriendRequest request) { return ResponseEntity.ok(service.updateRelationship(userId, request)); }

	@PostMapping("/feeds")
	public ResponseEntity<FeedUploadResponse> uploadFeed(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId, @RequestBody FeedUploadRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadFeed(userId, request)); }
	@GetMapping("/feeds")
	public ResponseEntity<List<FeedUploadResponse>> getFeedList(@RequestHeader(value = "X-User-Id", defaultValue = "1") long userId) { return ResponseEntity.ok(service.getFeedList(userId)); }
}
