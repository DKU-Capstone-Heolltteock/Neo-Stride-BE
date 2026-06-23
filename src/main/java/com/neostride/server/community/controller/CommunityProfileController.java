package com.neostride.server.community.controller;

import com.neostride.server.community.dto.AccountInfoResponse;
import com.neostride.server.community.dto.BadgeDetailResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.community.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Community Profile", description = "커뮤니티 프로필/계정/배지 API")
@RestController
public class CommunityProfileController {
	private final CommunityService service;
	private final CommunityUserContext userContext;
	private final CommunityMultipartSupport uploadSupport;

	public CommunityProfileController(
			CommunityService service,
			CommunityUserContext userContext,
			CommunityMultipartSupport uploadSupport
	) {
		this.service = service;
		this.userContext = userContext;
		this.uploadSupport = uploadSupport;
	}

	@Operation(summary = "내 프로필 조회")
	@GetMapping("/users/me/profile")
	public ResponseEntity<UserProfileResponse> getUserProfile(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getUserProfile(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping({"/users/{userId}/profile", "/community/runners/{userId}/profile", "/api/community/runners/{userId}/profile"})
	public ResponseEntity<UserProfileResponse> getRunnerProfile(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long userId
	) {
		return ResponseEntity.ok(service.getUserProfile(userContext.optionalUserId(authorization, headerUserId), userId));
	}

	@GetMapping("/users/me/account")
	public ResponseEntity<AccountInfoResponse> getAccountInfo(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getAccountInfo(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@Operation(summary = "내 상태 메시지 변경")
	@PatchMapping("/users/me/status")
	public ResponseEntity<Void> updateStatusMessage(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody Map<String, String> body
	) {
		service.updateStatusMessage(userContext.authenticatedUserId(authorization, headerUserId), body);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/users/me/nickname")
	public ResponseEntity<Void> updateNickname(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody Map<String, String> body
	) {
		service.updateNickname(userContext.authenticatedUserId(authorization, headerUserId), body);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/users/me")
	public ResponseEntity<Void> deleteAccount(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		service.deleteAccount(userContext.authenticatedUserId(authorization, headerUserId));
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "내 프로필 이미지 변경")
	@PatchMapping("/users/me/profile-image")
	public ResponseEntity<Void> updateProfileImage(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(value = "profile_image_url", required = false) String profileImageUrl,
			@RequestPart(value = "image", required = false) MultipartFile image,
			@RequestPart(value = "profile_photo", required = false) MultipartFile profilePhoto
	) {
		long userId = userContext.authenticatedUserId(authorization, headerUserId);
		String storedValue = CommunityMultipartSupport.normalizedNonBlank(profileImageUrl);
		MultipartFile upload = image != null ? image : profilePhoto;
		if (upload != null) {
			storedValue = uploadSupport.storeProfileImage(upload);
		}
		if (storedValue == null) {
			throw new IllegalArgumentException("profile_image_url, image 또는 profile_photo 중 하나는 필요합니다.");
		}
		service.updateProfileImage(userId, storedValue);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "내 프로필 이미지 기본값으로 변경")
	@DeleteMapping("/users/me/profile-image")
	public ResponseEntity<Void> deleteProfileImage(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		service.deleteProfileImage(userContext.authenticatedUserId(authorization, headerUserId));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/users/me/badge")
	public ResponseEntity<BadgeDetailResponse> getBadgeDetail(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getBadgeDetail(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping("/users/{userId}/badge")
	public ResponseEntity<BadgeDetailResponse> getUserBadgeDetail(@PathVariable long userId) {
		return ResponseEntity.ok(service.getBadgeDetail(userId));
	}
}
