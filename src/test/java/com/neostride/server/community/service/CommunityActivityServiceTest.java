package com.neostride.server.community.service;

import com.neostride.server.community.dto.MyCommentActivityResponse;
import com.neostride.server.community.repository.CommunityRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityActivityServiceTest {
	private final CommunityRepository repository = mock(CommunityRepository.class);
	private final CommunityService service = new CommunityService(repository);

	@Test
	void getMyCommentActivities_requestsOneExtraRowAndBuildsNextCursor() {
		MyCommentActivityResponse first = new MyCommentActivityResponse("TIP", 7L, 2L, "runner", null,
				false, "NONE", "FREE", "title", "body", "2026-06-02T10:00:00", null, null, null,
				false, null, List.of(), 0, 1, 0, false, false, true, false, false, 77L,
				"thanks", "2026-06-02T10:05:00", true);
		MyCommentActivityResponse second = new MyCommentActivityResponse("FEED", 8L, 3L, "runner2", null,
				false, "NONE", null, "title2", "body2", "2026-06-02T09:00:00", null, null, null,
				false, null, List.of(), 0, 1, 0, false, false, true, false, false, 76L,
				"nice", "2026-06-02T09:05:00", true);
		when(repository.myCommentActivities(1L, LocalDateTime.parse("2026-06-02T11:00:00"), 78L, 2))
				.thenReturn(List.of(first, second));

		var result = service.getMyCommentActivities(1L, "2026-06-02T11:00:00", 78L, 1);

		assertThat(result.items()).containsExactly(first);
		assertThat(result.hasMore()).isTrue();
		assertThat(result.nextCursor().createdAt()).isEqualTo("2026-06-02T10:05:00");
		assertThat(result.nextCursor().commentId()).isEqualTo(77L);
		verify(repository).myCommentActivities(1L, LocalDateTime.parse("2026-06-02T11:00:00"), 78L, 2);
	}

	@Test
	void getMyCommentActivities_rejectsInvalidUserIdBeforeRepositoryCall() {
		assertThatThrownBy(() -> service.getMyCommentActivities(0L, null, null, 20))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("user_id");

		verify(repository, never()).myCommentActivities(0L, null, null, 21);
	}

	@Test
	void getMyCommentActivities_rejectsInvalidLimitBeforeRepositoryCall() {
		assertThatThrownBy(() -> service.getMyCommentActivities(1L, null, null, 101))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("limit");

		verify(repository, never()).myCommentActivities(1L, null, null, 102);
	}

	@Test
	void getMyCommentActivities_rejectsPartialCursorBeforeRepositoryCall() {
		assertThatThrownBy(() -> service.getMyCommentActivities(1L, "2026-06-02T11:00:00", null, 20))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("cursorCreatedAt");

		verify(repository, never()).myCommentActivities(1L, LocalDateTime.parse("2026-06-02T11:00:00"), null, 21);
	}
}
