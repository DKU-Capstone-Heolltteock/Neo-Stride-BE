package com.neostride.server.community.controller;

import com.neostride.server.community.dto.CommentPageResponse;
import com.neostride.server.community.dto.CommentRequest;
import com.neostride.server.community.dto.CommentResponse;
import com.neostride.server.community.dto.TipDetailResponse;
import com.neostride.server.community.dto.TipListResponse;
import com.neostride.server.community.dto.TipUploadRequest;
import com.neostride.server.community.dto.TipUploadResponse;
import com.neostride.server.community.service.CommunityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Community Tips", description = "커뮤니티 팁 API")
@RestController
public class CommunityTipController {
	private final CommunityService service;
	private final CommunityUserContext userContext;
	private final CommunityMultipartSupport uploadSupport;

	public CommunityTipController(
			CommunityService service,
			CommunityUserContext userContext,
			CommunityMultipartSupport uploadSupport
	) {
		this.service = service;
		this.userContext = userContext;
		this.uploadSupport = uploadSupport;
	}

	@PostMapping("/api/tips")
	public ResponseEntity<TipUploadResponse> uploadTip(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody TipUploadRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadTip(userContext.authenticatedUserId(authorization, headerUserId), request));
	}

	@GetMapping("/api/tips")
	public ResponseEntity<TipListResponse> getTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getTips(userContext.optionalUserId(authorization, headerUserId)));
	}

	@PostMapping(value = "/api/community/tips", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TipUploadResponse> uploadCommunityTipJson(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody TipUploadRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadTip(userContext.authenticatedUserId(authorization, headerUserId), request));
	}

	@PostMapping(value = "/api/community/tips", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<TipUploadResponse> uploadTipMultipart(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam Map<String, String> fields,
			@RequestPart(value = "images", required = false) List<MultipartFile> images,
			@RequestPart(value = "route_image", required = false) MultipartFile routeImage,
			@RequestPart(value = "routeMapImage", required = false) MultipartFile routeMapImage
	) {
		long userId = userContext.authenticatedUserId(authorization, headerUserId);
		TipUploadRequest request = new TipUploadRequest(
				fields.get("category"),
				fields.get("title"),
				fields.get("content"),
				CommunityMultipartSupport.bool(fields.get("gpsVisible")),
				uploadSupport.storedRouteUri(routeImage != null ? routeImage : routeMapImage),
				CommunityMultipartSupport.first(fields, "courseAddress", "course_address"),
				uploadSupport.storedImageUris(images)
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadTip(userId, request));
	}

	@GetMapping("/api/community/tips")
	public ResponseEntity<List<TipUploadResponse>> getCommunityTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getTips(userContext.optionalUserId(authorization, headerUserId)).tips());
	}

	@GetMapping("/api/community/tips/likes")
	public ResponseEntity<List<TipUploadResponse>> getLikedTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getLikedTips(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping("/api/community/tips/bookmarks")
	public ResponseEntity<List<TipUploadResponse>> getBookmarkedTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getBookmarkedTips(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping("/api/community/tips/comments")
	public ResponseEntity<List<TipUploadResponse>> getCommentedTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getCommentedTips(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping("/api/community/tips/me")
	public ResponseEntity<List<TipUploadResponse>> getMyTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getMyTips(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping("/community/tips/user/{userId}")
	public ResponseEntity<List<TipUploadResponse>> getRunnerTips(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long userId
	) {
		return ResponseEntity.ok(service.getUserTips(userContext.optionalUserId(authorization, headerUserId), userId));
	}

	@GetMapping("/api/community/tips/{tipId}")
	public ResponseEntity<TipDetailResponse> getTipDetail(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long tipId
	) {
		return ResponseEntity.ok(service.getTipDetail(userContext.authenticatedUserId(authorization, headerUserId), tipId));
	}

	@PostMapping("/api/community/tips/{tipId}/likes")
	public ResponseEntity<Map<String, String>> toggleTipLike(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long tipId
	) {
		return ResponseEntity.ok(service.toggleTipLike(userContext.authenticatedUserId(authorization, headerUserId), tipId));
	}

	@PostMapping("/api/community/tips/{tipId}/bookmarks")
	public ResponseEntity<Map<String, String>> toggleTipBookmark(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long tipId
	) {
		return ResponseEntity.ok(service.toggleTipBookmark(userContext.authenticatedUserId(authorization, headerUserId), tipId));
	}

	@GetMapping("/api/community/tips/{tipId}/comments")
	public ResponseEntity<CommentPageResponse> getTipComments(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long tipId,
			@RequestParam(defaultValue = "20") int limit,
			@RequestParam(value = "cursorCreatedAt", required = false) String cursorCreatedAt,
			@RequestParam(value = "cursor_created_at", required = false) String cursorCreatedAtSnake,
			@RequestParam(value = "cursorId", required = false) Long cursorId,
			@RequestParam(value = "cursor_id", required = false) Long cursorIdSnake
	) {
		return ResponseEntity.ok(service.getTipComments(userContext.authenticatedUserId(authorization, headerUserId), tipId,
				CommunityMultipartSupport.firstNonBlank(cursorCreatedAt, cursorCreatedAtSnake),
				CommunityMultipartSupport.firstNonNull(cursorId, cursorIdSnake), limit));
	}

	@PostMapping("/api/community/tips/{tipId}/comments")
	public ResponseEntity<CommentResponse> createTipComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long tipId,
			@RequestBody CommentRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.createTipComment(userContext.authenticatedUserId(authorization, headerUserId), tipId, request));
	}

	@DeleteMapping("/api/community/tips/{tipId}")
	public ResponseEntity<Void> deleteTip(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long tipId
	) {
		service.deleteTip(userContext.authenticatedUserId(authorization, headerUserId), tipId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/api/community/tips/{tipId}")
	public ResponseEntity<TipUploadResponse> updateTip(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long tipId,
			@RequestBody TipUploadRequest request
	) {
		return ResponseEntity.ok(service.updateTip(userContext.authenticatedUserId(authorization, headerUserId), tipId, request));
	}

	@PutMapping("/api/community/tips/{tipId}/comments/{commentId}")
	public ResponseEntity<CommentResponse> updateTipComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long tipId,
			@PathVariable long commentId,
			@RequestBody CommentRequest request
	) {
		return ResponseEntity.ok(service.updateTipComment(userContext.authenticatedUserId(authorization, headerUserId), tipId, commentId, request));
	}

	@DeleteMapping("/api/community/tips/{tipId}/comments/{commentId}")
	public ResponseEntity<Void> deleteTipComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long tipId,
			@PathVariable long commentId
	) {
		service.deleteTipComment(userContext.authenticatedUserId(authorization, headerUserId), tipId, commentId);
		return ResponseEntity.noContent().build();
	}
}
