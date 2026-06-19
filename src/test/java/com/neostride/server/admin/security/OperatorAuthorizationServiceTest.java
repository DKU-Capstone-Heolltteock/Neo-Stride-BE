package com.neostride.server.admin.security;

import com.neostride.server.admin.repository.OperatorRepository;
import com.neostride.server.auth.exception.AuthenticationRequiredException;
import com.neostride.server.auth.exception.ForbiddenException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperatorAuthorizationServiceTest {
	private final OperatorTokenService tokenService = new OperatorTokenService(
			"test-secret-for-admin-authorization-service",
			3600,
			1209600
	);
	private final OperatorRepository operatorRepository = mock(OperatorRepository.class);
	private final OperatorAuthorizationService authorizationService = new OperatorAuthorizationService(tokenService, operatorRepository);

	@Test
	void requirePermission_returnsPrincipalWhenPermissionExists() {
		String token = tokenService.generateAccessToken(new OperatorPrincipal(
				3L,
				"moderator@example.com",
				"모더레이터",
				"MODERATOR",
				List.of(OperatorPermissions.REPORT_READ)
		));

		when(operatorRepository.findPrincipal(3L)).thenReturn(Optional.of(new OperatorPrincipal(
				3L,
				"moderator@example.com",
				"모더레이터",
				"MODERATOR",
				List.of(OperatorPermissions.REPORT_READ)
		)));

		OperatorPrincipal principal = authorizationService.requirePermission("Bearer " + token, OperatorPermissions.REPORT_READ);

		assertThat(principal.operatorAccountId()).isEqualTo(3L);
	}

	@Test
	void requirePermission_rejectsMissingPermission() {
		String token = tokenService.generateAccessToken(new OperatorPrincipal(
				3L,
				"moderator@example.com",
				"모더레이터",
				"MODERATOR",
				List.of(OperatorPermissions.REPORT_READ)
		));

		when(operatorRepository.findPrincipal(3L)).thenReturn(Optional.of(new OperatorPrincipal(
				3L,
				"moderator@example.com",
				"모더레이터",
				"MODERATOR",
				List.of(OperatorPermissions.REPORT_READ)
		)));

		assertThatThrownBy(() -> authorizationService.requirePermission("Bearer " + token, OperatorPermissions.ACCOUNT_SUSPEND))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void requireAuthenticated_rejectsMissingBearerToken() {
		assertThatThrownBy(() -> authorizationService.requireAuthenticated(null))
				.isInstanceOf(AuthenticationRequiredException.class);
	}

	@Test
	void requireAuthenticated_rejectsTokenWhenOperatorIsNoLongerActive() {
		String token = tokenService.generateAccessToken(new OperatorPrincipal(
				3L,
				"moderator@example.com",
				"모더레이터",
				"MODERATOR",
				List.of(OperatorPermissions.REPORT_READ)
		));
		when(operatorRepository.findPrincipal(3L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authorizationService.requireAuthenticated("Bearer " + token))
				.isInstanceOf(AuthenticationRequiredException.class);
	}

}
