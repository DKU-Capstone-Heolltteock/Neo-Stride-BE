package com.neostride.server.community.service;

import com.neostride.server.community.dto.MyCommentActivityResponse;
import com.neostride.server.community.repository.CommunityRepository;
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
	void getMyCommentActivities_delegatesAfterUserValidation() {
		MyCommentActivityResponse row = new MyCommentActivityResponse("TIP", 7L, 2L, "runner", null,
				false, "NONE", "FREE", "title", "body", "2026-06-02T10:00:00", null, null, null,
				false, null, List.of(), 0, 1, 0, false, false, true, false, false, 77L,
				"thanks", "2026-06-02T10:05:00", true);
		when(repository.myCommentActivities(1L)).thenReturn(List.of(row));

		List<MyCommentActivityResponse> result = service.getMyCommentActivities(1L);

		assertThat(result).containsExactly(row);
		verify(repository).myCommentActivities(1L);
	}

	@Test
	void getMyCommentActivities_rejectsInvalidUserIdBeforeRepositoryCall() {
		assertThatThrownBy(() -> service.getMyCommentActivities(0L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("user_id");

		verify(repository, never()).myCommentActivities(0L);
	}
}
