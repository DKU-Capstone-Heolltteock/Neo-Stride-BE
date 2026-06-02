package com.neostride.server.community.controller;

import com.neostride.server.community.dto.MyCommentActivityResponse;
import com.neostride.server.community.service.CommunityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
	public ResponseEntity<List<MyCommentActivityResponse>> getMyCommentActivities(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ResponseEntity.ok(service.getMyCommentActivities(userContext.authenticatedUserId(authorization, headerUserId)));
	}
}
