package com.neostride.server.community.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.community.dto.CommentCursorResponse;
import com.neostride.server.community.dto.MyCommentActivityPageResponse;
import com.neostride.server.community.dto.MyCommentActivityResponse;
import com.neostride.server.community.service.CommunityService;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityActivityControllerTest {
	private static final String AUTHORIZATION = "Bearer access-token";

	private final CommunityService service = mock(CommunityService.class);
	private final AuthenticatedUserService authenticatedUserService = mock(AuthenticatedUserService.class);
	private final CommunityUserContext userContext = new CommunityUserContext(authenticatedUserService);
	private final CommunityActivityController controller = new CommunityActivityController(service, userContext);

	@Test
	void getMyCommentActivities_returnsAuthenticatedUsersCommentActivityPage() {
		MyCommentActivityResponse row = new MyCommentActivityResponse("FEED", 10L, 2L, "runner", null,
				false, "NONE", null, "title", "body", "2026-06-02T10:00:00", null, null, null,
				false, null, List.of(), 1, 1, 0, false, false, true, false, false, 99L,
				"nice", "2026-06-02T10:05:00", true);
		MyCommentActivityPageResponse page = new MyCommentActivityPageResponse(
				List.of(row), new CommentCursorResponse("2026-06-02T10:05:00", 99L), false);
		when(authenticatedUserService.requireUserId(AUTHORIZATION)).thenReturn(1L);
		when(service.getMyCommentActivities(1L, "2026-06-02T11:00:00", 100L, 20)).thenReturn(page);

		var response = controller.getMyCommentActivities(AUTHORIZATION, 1L, 20,
				null, "2026-06-02T11:00:00", null, 100L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(page);
		verify(service).getMyCommentActivities(1L, "2026-06-02T11:00:00", 100L, 20);
	}

	@Test
	void getMyCommentActivities_declaresDefaultLimit() throws Exception {
		Method method = CommunityActivityController.class.getMethod("getMyCommentActivities",
				String.class, Long.class, int.class, String.class, String.class, Long.class, Long.class);
		Parameter limitParameter = method.getParameters()[2];

		RequestParam requestParam = limitParameter.getAnnotation(RequestParam.class);

		assertThat(requestParam.defaultValue()).isEqualTo("20");
	}
}
