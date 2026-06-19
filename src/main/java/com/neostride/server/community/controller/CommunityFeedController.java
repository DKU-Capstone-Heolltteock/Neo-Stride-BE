package com.neostride.server.community.controller;

import com.neostride.server.community.dto.CommentPageResponse;
import com.neostride.server.community.dto.CommentRequest;
import com.neostride.server.community.dto.CommentResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedDetailResponse;
import com.neostride.server.community.dto.FeedPageResponse;
import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendResponse;
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

@Tag(name = "Community Feeds", description = "커뮤니티 피드/콘텐츠 API")
@RestController
public class CommunityFeedController {
	private final CommunityService service;
	private final CommunityUserContext userContext;
	private final CommunityMultipartSupport uploadSupport;

	public CommunityFeedController(
			CommunityService service,
			CommunityUserContext userContext,
			CommunityMultipartSupport uploadSupport
	) {
		this.service = service;
		this.userContext = userContext;
		this.uploadSupport = uploadSupport;
	}

	@GetMapping("/community/contents/me")
	public ResponseEntity<List<CommunityContentResponse>> getMyFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getMyFeeds(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping("/community/contents/tagged")
	public ResponseEntity<List<CommunityContentResponse>> getTaggedFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getTaggedFeeds(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping("/community/contents/comments")
	public ResponseEntity<List<CommunityContentResponse>> getCommentedFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getCommentedFeeds(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping("/community/contents/likes")
	public ResponseEntity<List<CommunityContentResponse>> getLikedFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getLikedFeeds(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping("/community/contents/bookmarks")
	public ResponseEntity<List<CommunityContentResponse>> getBookmarkedFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getBookmarkedFeeds(userContext.authenticatedUserId(authorization, headerUserId)));
	}

	@GetMapping("/community/contents/user/{userId}")
	public ResponseEntity<List<CommunityContentResponse>> getRunnerFeeds(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long userId
	) {
		return ResponseEntity.ok(service.getUserFeeds(userContext.optionalUserId(authorization, headerUserId), userId));
	}

	@PostMapping("/community/bookmark/{contentId}")
	public ResponseEntity<Map<String, String>> toggleBookmark(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long contentId
	) {
		return ResponseEntity.ok(service.toggleBookmark(userContext.authenticatedUserId(authorization, headerUserId), contentId));
	}

	@PostMapping("/feeds")
	public ResponseEntity<FeedUploadResponse> uploadFeed(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody FeedUploadRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadFeed(userContext.authenticatedUserId(authorization, headerUserId), request));
	}

	@GetMapping("/api/community/feeds/{feedId}")
	public ResponseEntity<FeedDetailResponse> getFeedDetail(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long feedId
	) {
		return ResponseEntity.ok(service.getFeedDetail(userContext.authenticatedUserId(authorization, headerUserId), feedId));
	}

	@PostMapping("/api/community/feeds/{feedId}/likes")
	public ResponseEntity<Map<String, String>> toggleFeedLike(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long feedId
	) {
		return ResponseEntity.ok(service.toggleFeedLike(userContext.authenticatedUserId(authorization, headerUserId), feedId));
	}

	@PostMapping("/api/community/feeds/{feedId}/bookmarks")
	public ResponseEntity<Map<String, String>> toggleFeedBookmark(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long feedId
	) {
		return ResponseEntity.ok(service.toggleFeedBookmark(userContext.authenticatedUserId(authorization, headerUserId), feedId));
	}

	@GetMapping("/api/community/feeds/{feedId}/comments")
	public ResponseEntity<CommentPageResponse> getFeedComments(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long feedId,
			@RequestParam(defaultValue = "20") int limit,
			@RequestParam(value = "cursorCreatedAt", required = false) String cursorCreatedAt,
			@RequestParam(value = "cursor_created_at", required = false) String cursorCreatedAtSnake,
			@RequestParam(value = "cursorId", required = false) Long cursorId,
			@RequestParam(value = "cursor_id", required = false) Long cursorIdSnake
	) {
		return ResponseEntity.ok(service.getFeedComments(userContext.authenticatedUserId(authorization, headerUserId), feedId,
				CommunityMultipartSupport.firstNonBlank(cursorCreatedAt, cursorCreatedAtSnake),
				CommunityMultipartSupport.firstNonNull(cursorId, cursorIdSnake), limit));
	}

	@PostMapping("/api/community/feeds/{feedId}/comments")
	public ResponseEntity<CommentResponse> createFeedComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long feedId,
			@RequestBody CommentRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.createFeedComment(userContext.authenticatedUserId(authorization, headerUserId), feedId, request));
	}

	@DeleteMapping("/api/community/feeds/{feedId}")
	public ResponseEntity<Void> deleteFeed(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long feedId
	) {
		service.deleteFeed(userContext.authenticatedUserId(authorization, headerUserId), feedId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/api/community/feeds/{feedId}")
	public ResponseEntity<FeedUploadResponse> updateFeed(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long feedId,
			@RequestBody FeedUploadRequest request
	) {
		return ResponseEntity.ok(service.updateFeed(userContext.authenticatedUserId(authorization, headerUserId), feedId, request));
	}

	@PutMapping("/api/community/feeds/{feedId}/comments/{commentId}")
	public ResponseEntity<CommentResponse> updateFeedComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long feedId,
			@PathVariable long commentId,
			@RequestBody CommentRequest request
	) {
		return ResponseEntity.ok(service.updateFeedComment(userContext.authenticatedUserId(authorization, headerUserId), feedId, commentId, request));
	}

	@DeleteMapping("/api/community/feeds/{feedId}/comments/{commentId}")
	public ResponseEntity<Void> deleteFeedComment(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long feedId,
			@PathVariable long commentId
	) {
		service.deleteFeedComment(userContext.authenticatedUserId(authorization, headerUserId), feedId, commentId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/community/feeds/{feedId}/tagged-users")
	public ResponseEntity<List<FriendResponse>> getTaggedUsers(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@PathVariable long feedId
	) {
		return ResponseEntity.ok(service.getTaggedUsers(userContext.optionalUserId(authorization, headerUserId), feedId));
	}

	@PostMapping(value = "/api/community/feeds", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<FeedUploadResponse> uploadCommunityFeedJson(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestBody FeedUploadRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadFeed(userContext.authenticatedUserId(authorization, headerUserId), request));
	}

	@PostMapping(value = "/api/community/feeds", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<FeedUploadResponse> uploadFeedMultipart(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam Map<String, String> fields,
			@RequestPart(value = "images", required = false) List<MultipartFile> images,
			@RequestPart(value = "route_image", required = false) MultipartFile routeImage,
			@RequestPart(value = "routeMapImage", required = false) MultipartFile routeMapImage,
			@RequestPart(value = "route_map_image", required = false) MultipartFile routeMapImageSnake
	) {
		FeedUploadRequest request = new FeedUploadRequest(
				fields.get("title"), fields.get("content"), fields.get("privacy"),
				CommunityMultipartSupport.bool(fields.get("mapVisible"), fields.get("map_visible")),
				uploadSupport.storedRouteUri(routeImage != null ? routeImage : (routeMapImage != null ? routeMapImage : routeMapImageSnake)),
				CommunityMultipartSupport.parseLongList(CommunityMultipartSupport.first(fields, "taggedUserIds", "tagged_user_ids")),
				uploadSupport.storedImageUris(images),
				CommunityMultipartSupport.decimal(CommunityMultipartSupport.first(fields, "distance", "total_distance", "totalDistance")),
				CommunityMultipartSupport.first(fields, "runningTime", "running_time", "duration"),
				CommunityMultipartSupport.first(fields, "pace", "running_pace", "runningPace"),
				CommunityMultipartSupport.parseInt(fields, "tagCount", "tag_count"),
				CommunityMultipartSupport.firstLong(fields, "running_record_id", "run_record_id", "runningRecordId", "runRecordId")
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadFeed(userContext.authenticatedUserId(authorization, headerUserId), request));
	}

	@GetMapping("/feeds")
	public ResponseEntity<List<FeedUploadResponse>> getFeedList() {
		return ResponseEntity.ok(service.getFeedList());
	}

	@GetMapping("/api/community/feeds")
	public ResponseEntity<List<FeedUploadResponse>> getCommunityFeedList(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(required = false) Integer limit,
			@RequestParam(value = "cursorCreatedAt", required = false) String cursorCreatedAt,
			@RequestParam(value = "cursor_created_at", required = false) String cursorCreatedAtSnake,
			@RequestParam(value = "cursorId", required = false) Long cursorId,
			@RequestParam(value = "cursor_id", required = false) Long cursorIdSnake
	) {
		return ResponseEntity.ok(service.getFeedList(userContext.optionalUserId(authorization, headerUserId),
				CommunityMultipartSupport.firstNonBlank(cursorCreatedAt, cursorCreatedAtSnake),
				CommunityMultipartSupport.firstNonNull(cursorId, cursorIdSnake), limit));
	}

	@GetMapping("/api/community/feeds/page")
	public ResponseEntity<FeedPageResponse> getCommunityFeedPage(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(defaultValue = "20") int limit,
			@RequestParam(value = "cursorCreatedAt", required = false) String cursorCreatedAt,
			@RequestParam(value = "cursor_created_at", required = false) String cursorCreatedAtSnake,
			@RequestParam(value = "cursorId", required = false) Long cursorId,
			@RequestParam(value = "cursor_id", required = false) Long cursorIdSnake
	) {
		return ResponseEntity.ok(service.getFeedPage(userContext.optionalUserId(authorization, headerUserId),
				CommunityMultipartSupport.firstNonBlank(cursorCreatedAt, cursorCreatedAtSnake),
				CommunityMultipartSupport.firstNonNull(cursorId, cursorIdSnake), limit));
	}
}
