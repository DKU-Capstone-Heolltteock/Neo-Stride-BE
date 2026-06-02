package com.neostride.server.community.controller;

import com.neostride.server.community.dto.FriendRequest;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.community.service.CommunityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Community Relationships", description = "커뮤니티 친구/관계 API")
@RestController
public class CommunityRelationshipController {
	private final CommunityService service;
	private final CommunityUserContext userContext;

	public CommunityRelationshipController(CommunityService service, CommunityUserContext userContext) {
		this.service = service;
		this.userContext = userContext;
	}

	@GetMapping("/community/friends")
	public ResponseEntity<List<FriendResponse>> getCommunityFriends(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam String status
	) {
		return ResponseEntity.ok(service.getFriendList(userContext.authenticatedUserId(authorization, headerUserId), status));
	}

	@GetMapping("/community/friends/user/{userId}")
	public ResponseEntity<List<FriendResponse>> getUserFriendList(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long userId
	) {
		return ResponseEntity.ok(service.getUserFriendList(userContext.optionalUserId(authorization, headerUserId), userId));
	}

	@GetMapping("/api/friends/{userId}")
	public ResponseEntity<List<FriendResponse>> getApiUserFriendList(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long userId
	) {
		return ResponseEntity.ok(service.getUserFriendList(userContext.authenticatedUserId(authorization, headerUserId), userId));
	}

	@PostMapping({"/community/friends/action", "/api/community/friends/action"})
	public ResponseEntity<Map<String, String>> updateCommunityRelationship(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody FriendRequest request
	) {
		return ResponseEntity.ok(service.updateRelationship(userContext.authenticatedUserId(authorization, headerUserId), request));
	}

	@GetMapping("/api/relationships")
	public ResponseEntity<List<FriendResponse>> getLegacyRelationships(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam String status
	) {
		return ResponseEntity.ok(service.getFriendList(userContext.authenticatedUserId(authorization, headerUserId), status));
	}

	@PostMapping("/api/relationships/action")
	public ResponseEntity<Map<String, String>> updateLegacyRelationship(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody FriendRequest request
	) {
		return ResponseEntity.ok(service.updateRelationship(userContext.authenticatedUserId(authorization, headerUserId), request));
	}

	@GetMapping("/api/community/friends")
	public ResponseEntity<List<FriendResponse>> getApiCommunityFriends(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam String status
	) {
		return ResponseEntity.ok(service.getFriendList(userContext.authenticatedUserId(authorization, headerUserId), status));
	}
}
