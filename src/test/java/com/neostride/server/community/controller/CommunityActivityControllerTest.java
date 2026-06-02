package com.neostride.server.community.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.community.dto.MyCommentActivityResponse;
import com.neostride.server.community.service.CommunityService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityActivityControllerTest {
	private static final String AUTHORIZATION = "Bearer access-token";

	private final CommunityService service = mock(CommunityService.class);
	private final AuthenticatedUserService authenticatedUserService = mock(AuthenticatedUserService.class);
	private final CommunityUserContext userContext = new CommunityUserContext(authenticatedUserService);
	private final CommunityActivityController controller = new CommunityActivityController(service, userContext);

	@Test
	void getMyCommentActivities_returnsAuthenticatedUsersCommentActivityRows() {
		MyCommentActivityResponse row = new MyCommentActivityResponse("FEED", 10L, 2L, "runner", null,
				false, "NONE", null, "title", "body", "2026-06-02T10:00:00", null, null, null,
				false, null, List.of(), 1, 1, 0, false, false, true, false, false, 99L,
				"nice", "2026-06-02T10:05:00", true);
		when(authenticatedUserService.requireUserId(AUTHORIZATION)).thenReturn(1L);
		when(service.getMyCommentActivities(1L)).thenReturn(List.of(row));

		var response = controller.getMyCommentActivities(AUTHORIZATION, 1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsExactly(row);
	}
}
