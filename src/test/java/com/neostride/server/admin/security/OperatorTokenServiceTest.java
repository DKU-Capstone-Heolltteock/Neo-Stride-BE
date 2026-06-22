package com.neostride.server.admin.security;

import com.neostride.server.auth.service.JwtTokenService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorTokenServiceTest {
	private static final String SECRET = "test-secret-for-admin-operator-token-service";

	private final OperatorTokenService operatorTokenService = new OperatorTokenService(SECRET, 3600, 1209600);

	@Test
	void generateAccessToken_containsAdminAudienceTypeAndPermissions() {
		OperatorPrincipal principal = new OperatorPrincipal(
				7L,
				"ops@example.com",
				"운영자",
				"MODERATOR",
				List.of(OperatorPermissions.ACCOUNT_READ, OperatorPermissions.REPORT_READ)
		);

		String token = operatorTokenService.generateAccessToken(principal);
		OperatorTokenService.OperatorTokenClaims claims = operatorTokenService.verify(token);

		assertThat(claims.operatorAccountId()).isEqualTo(7L);
		assertThat(claims.email()).isEqualTo("ops@example.com");
		assertThat(claims.type()).isEqualTo("operator_access");
		assertThat(claims.audience()).isEqualTo("admin");
		assertThat(claims.permissions()).containsExactly(OperatorPermissions.ACCOUNT_READ, OperatorPermissions.REPORT_READ);
	}

	@Test
	void verify_rejectsUserJwtEvenWithSameSecret() {
		JwtTokenService userTokenService = new JwtTokenService(SECRET, 3600, 1209600);
		String userToken = userTokenService.generateAccessToken(1L, "runner@example.com", "홍길동");

		assertThatThrownBy(() -> operatorTokenService.verify(userToken))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
