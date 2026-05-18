package com.neostride.server.auth.service;

import com.neostride.server.auth.exception.AuthenticationRequiredException;
import com.neostride.server.auth.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedUserServiceTest {

	private final JwtTokenService jwtTokenService = new JwtTokenService(
			"test-secret-for-authenticated-user-service",
			3600,
			1209600
	);
	private final AuthenticatedUserService authenticatedUserService = new AuthenticatedUserService(jwtTokenService);

	@Test
	void requireUserId_acceptsAccessBearerToken() {
		String token = jwtTokenService.generateAccessToken(7L, "runner@example.com", "runner");

		long userId = authenticatedUserService.requireUserId("Bearer " + token);

		assertThat(userId).isEqualTo(7L);
	}

	@Test
	void requireUserId_rejectsRefreshToken() {
		String token = jwtTokenService.generateRefreshToken(7L, "runner@example.com", "runner");

		assertThatThrownBy(() -> authenticatedUserService.requireUserId("Bearer " + token))
				.isInstanceOf(AuthenticationRequiredException.class);
	}

	@Test
	void requireSameUser_rejectsMismatchedRequestedUserId() {
		assertThatThrownBy(() -> authenticatedUserService.requireSameUser(7L, 8L, "user_id"))
				.isInstanceOf(ForbiddenException.class);
	}
}
