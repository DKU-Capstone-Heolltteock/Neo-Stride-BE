package com.neostride.server.community.service;

import com.neostride.server.community.dto.SearchUserResponse;
import com.neostride.server.community.repository.CommunityRepository;
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
