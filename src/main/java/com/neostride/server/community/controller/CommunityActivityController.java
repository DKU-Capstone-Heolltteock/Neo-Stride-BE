package com.neostride.server.community.controller;

import com.neostride.server.community.dto.MyCommentActivityPageResponse;
import com.neostride.server.community.service.CommunityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Community Activity", description = "커뮤니티 활동 조회 API")
@RestController
public class CommunityActivityController {
	private final CommunityService service;
	private final CommunityUserContext userContext;

	public CommunityActivityController(CommunityService service, CommunityUserContext userContext) {
		this.service = service;
		this.userContext = userContext;
	}

	@GetMapping("/api/community/comments/me")
	public ResponseEntity<MyCommentActivityPageResponse> getMyCommentActivities(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
			@RequestParam(defaultValue = "20") int limit,
			@RequestParam(value = "cursorCreatedAt", required = false) String cursorCreatedAt,
			@RequestParam(value = "cursor_created_at", required = false) String cursorCreatedAtSnake,
			@RequestParam(value = "cursorId", required = false) Long cursorId,
			@RequestParam(value = "cursor_id", required = false) Long cursorIdSnake
	) {
		return ResponseEntity.ok(service.getMyCommentActivities(userContext.authenticatedUserId(authorization, headerUserId),
				CommunityMultipartSupport.firstNonBlank(cursorCreatedAt, cursorCreatedAtSnake),
				CommunityMultipartSupport.firstNonNull(cursorId, cursorIdSnake), limit));
	}
}
