package com.neostride.server.community.service;

import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.SearchUserResponse;
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

class CommunityServiceTest {

	private final CommunityRepository repository = mock(CommunityRepository.class);
	private final CommunityService service = new CommunityService(repository);


	@Test
	void getUserFeeds_returnsOnlyPublicFeedsForPublicProfileEndpoint() {
		when(repository.feedsByUserForViewer(null, 2L)).thenReturn(List.of());

		assertThat(service.getUserFeeds(2L)).isEmpty();

		verify(repository).feedsByUserForViewer(null, 2L);
		verify(repository, never()).myFeeds(2L);
	}

	@Test
	void getUserFeedsWithViewerUsesViewerAwareRunnerPageQuery() {
		when(repository.feedsByUserForViewer(1L, 2L)).thenReturn(List.of());

		assertThat(service.getUserFeeds(1L, 2L)).isEmpty();

		verify(repository).feedsByUserForViewer(1L, 2L);
	}


	@Test
	void getFeedListWithLimitKeepsLegacyShapeWhileUsingCursorQuery() {
		FeedUploadResponse first = new FeedUploadResponse(76L, null, "neo", "2026-05-26T22:14:32", "title", "body", 0, 0, 0, "1.00 km", null, null, false, null, List.of());
		when(repository.listFeedsPage(null, null, null, 2)).thenReturn(List.of(first));

		List<FeedUploadResponse> result = service.getFeedList(null, null, null, 1);

		assertThat(result).containsExactly(first);
		verify(repository).listFeedsPage(null, null, null, 2);
	}

	@Test
	void getFeedCommentsBuildsCursorPage() {
		var first = new com.neostride.server.community.dto.CommentResponse(5L, 2L, "runner", null, "nice", "2026-05-28T10:00:00", false, "NONE", false);
		var second = new com.neostride.server.community.dto.CommentResponse(6L, 3L, "runner2", null, "good", "2026-05-28T10:01:00", false, "NONE", false);
		when(repository.commentsPage(1L, 99L, LocalDateTime.parse("2026-05-28T09:00:00"), 4L, 2)).thenReturn(List.of(first, second));

		var result = service.getFeedComments(1L, 99L, "2026-05-28T09:00:00", 4L, 1);

		assertThat(result.items()).containsExactly(first);
		assertThat(result.hasMore()).isTrue();
		assertThat(result.nextCursor().commentId()).isEqualTo(5L);
	}

	@Test
	void getFeedPage_requestsOneExtraRowAndBuildsNextCursor() {
		FeedUploadResponse first = new FeedUploadResponse(76L, null, "neo", "2026-05-26T22:14:32", "title", "body", 0, 0, 0, "1.00 km", null, null, false, null, List.of());
		FeedUploadResponse second = new FeedUploadResponse(72L, null, "neo", "2026-05-26T20:40:39", "title", "body", 0, 0, 0, "1.00 km", null, null, false, null, List.of());
		when(repository.listFeedsPage(1L, LocalDateTime.parse("2026-05-26T23:00:00"), 80L, 2)).thenReturn(List.of(first, second));

		var result = service.getFeedPage(1L, "2026-05-26T23:00:00", 80L, 1);

		assertThat(result.items()).containsExactly(first);
		assertThat(result.hasMore()).isTrue();
		assertThat(result.nextCursor().createdAt()).isEqualTo("2026-05-26T22:14:32");
		assertThat(result.nextCursor().feedId()).isEqualTo(76L);
	}

	@Test
	void getFeedPage_rejectsPartialCursor() {
		assertThatThrownBy(() -> service.getFeedPage(null, "2026-05-26T23:00:00", null, 20))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("cursorCreatedAt");

		verify(repository, never()).listFeedsPage(null, LocalDateTime.parse("2026-05-26T23:00:00"), null, 21);
	}

	@Test
	void searchProfiles_returnsTopProfilesWhenKeywordIsBlank() {
		SearchUserResponse user = new SearchUserResponse(1L, "neo", null, null, 0, "GOLD", "none");
		when(repository.getTopProfiles(0, 10)).thenReturn(List.of(user));

		List<SearchUserResponse> result = service.searchProfiles("   ", 0, 10);

		assertThat(result).containsExactly(user);
		verify(repository).getTopProfiles(0, 10);
		verify(repository, never()).searchProfiles("   ", 0, 10);
	}

	@Test
	void searchProfiles_delegatesWhenKeywordIsPresent() {
		SearchUserResponse user = new SearchUserResponse(1L, "neo", null, null, 0, "NONE", "none");
		when(repository.searchProfiles("neo", 0, 10)).thenReturn(List.of(user));

		List<SearchUserResponse> result = service.searchProfiles("neo", 0, 10);

		assertThat(result).containsExactly(user);
		verify(repository).searchProfiles("neo", 0, 10);
	}

	@Test
	void searchProfiles_rejectsSqlInjectionPatternsBeforeRepositoryCall() {
		assertThatThrownBy(() -> service.searchProfiles("neo' OR '1'='1", 0, 10))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("검색어");

		verify(repository, never()).searchProfiles("neo' OR '1'='1", 0, 10);
	}

	@Test
	void getTopProfiles_delegatesToRepository() {
		SearchUserResponse user = new SearchUserResponse(1L, "neo", null, null, 0, "GOLD", "none");
		when(repository.getTopProfiles(0, 10)).thenReturn(List.of(user));

		List<SearchUserResponse> result = service.getTopProfiles(0, 10);

		assertThat(result).containsExactly(user);
		verify(repository).getTopProfiles(0, 10);
	}
}
