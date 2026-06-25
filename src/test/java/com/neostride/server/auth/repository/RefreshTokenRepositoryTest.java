package com.neostride.server.auth.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenRepositoryTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final RefreshTokenRepository repository = new RefreshTokenRepository(jdbcTemplate);

	@Test
	void saveStoresHashedTokenIdOnly() {
		ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);

		repository.save(1L, "refresh-id", 123L);

		verify(jdbcTemplate).update(anyString(), eq(1L), tokenHash.capture(), eq(123L));
		assertThat(tokenHash.getValue()).hasSize(64);
		assertThat(tokenHash.getValue()).doesNotContain("refresh-id");
	}

	@Test
	void revokeIfActiveReturnsTrueWhenSingleRowIsRevoked() {
		when(jdbcTemplate.update(anyString(), eq(1L), anyString())).thenReturn(1);

		assertThat(repository.revokeIfActive(1L, "refresh-id")).isTrue();
	}

	@Test
	void revokeAllForUserRevokesEveryActiveTokenForUser() {
		repository.revokeAllForUser(7L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).update(sql.capture(), eq(7L));
		assertThat(sql.getValue())
				.contains("UPDATE refresh_tokens")
				.contains("revoked_at")
				.contains("WHERE user_id = ? AND revoked_at IS NULL");
	}

}
