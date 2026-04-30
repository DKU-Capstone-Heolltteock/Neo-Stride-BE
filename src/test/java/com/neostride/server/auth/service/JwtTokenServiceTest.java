package com.neostride.server.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

	private final JwtTokenService jwtTokenService = new JwtTokenService(
			"test-secret-for-jwt-token-service-that-is-long-enough",
			3600,
			1209600
	);

	@Test
	void generateAccessToken_containsUserClaimsAndCanBeVerified() {
		String token = jwtTokenService.generateAccessToken(1L, "runner@example.com", "홍길동");

		JwtTokenService.TokenClaims claims = jwtTokenService.verify(token);

		assertThat(token).isNotBlank();
		assertThat(claims.userId()).isEqualTo(1L);
		assertThat(claims.email()).isEqualTo("runner@example.com");
		assertThat(claims.name()).isEqualTo("홍길동");
		assertThat(claims.type()).isEqualTo("access");
	}

	@Test
	void generateRefreshToken_containsRefreshType() {
		String token = jwtTokenService.generateRefreshToken(1L, "runner@example.com", "홍길동");

		JwtTokenService.TokenClaims claims = jwtTokenService.verify(token);

		assertThat(claims.type()).isEqualTo("refresh");
	}
}
