package com.neostride.server.auth.service;

import com.neostride.server.auth.dto.LoginRequest;
import com.neostride.server.auth.dto.LoginResponse;
import com.neostride.server.auth.exception.InvalidCredentialsException;
import com.neostride.server.auth.repository.RefreshTokenRepository;
import com.neostride.server.auth.repository.UserRepository;
import com.neostride.server.auth.repository.UserRow;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceLoginTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final PasswordHashService passwordHashService = mock(PasswordHashService.class);
	private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
	private final AuthService authService = new AuthService(userRepository, passwordHashService, jwtTokenService);

	@Test
	void login_withValidCredentials_returnsTokens() {
		String storedHash = "pbkdf2_sha256$hash";
		when(userRepository.findByEmail("runner@example.com"))
				.thenReturn(Optional.of(new UserRow(1L, "runner@example.com", "홍길동", storedHash)));
		when(passwordHashService.matches("plain-password", storedHash)).thenReturn(true);
		when(jwtTokenService.generateAccessToken(1L, "runner@example.com", "홍길동")).thenReturn("access-token");
		when(jwtTokenService.generateRefreshToken(1L, "runner@example.com", "홍길동")).thenReturn("refresh-token");

		LoginResponse response = authService.login(new LoginRequest("RUNNER@example.com", "plain-password"));

		assertThat(response.status()).isEqualTo("success");
		assertThat(response.userId()).isEqualTo(1L);
		assertThat(response.email()).isEqualTo("runner@example.com");
		assertThat(response.name()).isEqualTo("홍길동");
		assertThat(response.nickname()).isEqualTo("홍길동");
		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
	}

	@Test
	void login_withRefreshTokenRepository_persistsRefreshTokenId() {
		RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
		AuthService service = new AuthService(userRepository, passwordHashService, jwtTokenService, refreshTokenRepository);
		String storedHash = "pbkdf2_sha256$hash";
		when(userRepository.findByEmail("runner@example.com"))
				.thenReturn(Optional.of(new UserRow(1L, "runner@example.com", "홍길동", storedHash)));
		when(passwordHashService.matches("plain-password", storedHash)).thenReturn(true);
		when(jwtTokenService.generateAccessToken(1L, "runner@example.com", "홍길동")).thenReturn("access-token");
		when(jwtTokenService.generateRefreshToken(1L, "runner@example.com", "홍길동")).thenReturn("refresh-token");
		when(jwtTokenService.verify("refresh-token")).thenReturn(new JwtTokenService.TokenClaims(1L, "runner@example.com", "홍길동", "refresh", "refresh-id", 123L));

		service.login(new LoginRequest("runner@example.com", "plain-password"));

		verify(refreshTokenRepository).save(1L, "refresh-id", 123L);
	}


	@Test
	void login_withWrongPassword_throwsInvalidCredentials() {
		String storedHash = "pbkdf2_sha256$hash";
		when(userRepository.findByEmail("runner@example.com"))
				.thenReturn(Optional.of(new UserRow(1L, "runner@example.com", "홍길동", storedHash)));
		when(passwordHashService.matches("wrong-password", storedHash)).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest("runner@example.com", "wrong-password")))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
	}



	@Test
	void refresh_withValidRefreshToken_issuesNewTokens() {
		when(jwtTokenService.verify("refresh-token")).thenReturn(new JwtTokenService.TokenClaims(1L, "runner@example.com", "홍길동", "refresh", 0L));
		when(jwtTokenService.generateAccessToken(1L, "runner@example.com", "홍길동")).thenReturn("new-access-token");
		when(jwtTokenService.generateRefreshToken(1L, "runner@example.com", "홍길동")).thenReturn("new-refresh-token");

		LoginResponse response = authService.refresh("refresh-token");

		assertThat(response.status()).isEqualTo("success");
		assertThat(response.userId()).isEqualTo(1L);
		assertThat(response.accessToken()).isEqualTo("new-access-token");
		assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
	}

	@Test
	void refresh_withPersistedRefreshToken_rotatesStoredToken() {
		RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
		AuthService service = new AuthService(userRepository, passwordHashService, jwtTokenService, refreshTokenRepository);
		when(jwtTokenService.verify("refresh-token")).thenReturn(new JwtTokenService.TokenClaims(1L, "runner@example.com", "홍길동", "refresh", "old-id", 123L));
		when(refreshTokenRepository.revokeIfActive(1L, "old-id")).thenReturn(true);
		when(jwtTokenService.generateAccessToken(1L, "runner@example.com", "홍길동")).thenReturn("new-access-token");
		when(jwtTokenService.generateRefreshToken(1L, "runner@example.com", "홍길동")).thenReturn("new-refresh-token");
		when(jwtTokenService.verify("new-refresh-token")).thenReturn(new JwtTokenService.TokenClaims(1L, "runner@example.com", "홍길동", "refresh", "new-id", 456L));

		LoginResponse response = service.refresh("refresh-token");

		assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
		verify(refreshTokenRepository).revokeIfActive(1L, "old-id");
		verify(refreshTokenRepository).save(1L, "new-id", 456L);
	}

	@Test
	void refresh_withReusedPersistedRefreshToken_throwsInvalidCredentials() {
		RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
		AuthService service = new AuthService(userRepository, passwordHashService, jwtTokenService, refreshTokenRepository);
		when(jwtTokenService.verify("refresh-token")).thenReturn(new JwtTokenService.TokenClaims(1L, "runner@example.com", "홍길동", "refresh", "old-id", 123L));
		when(refreshTokenRepository.revokeIfActive(1L, "old-id")).thenReturn(false);

		assertThatThrownBy(() -> service.refresh("refresh-token"))
				.isInstanceOf(InvalidCredentialsException.class);

		verify(jwtTokenService, never()).generateAccessToken(1L, "runner@example.com", "홍길동");
	}


	@Test
	void refresh_withAccessToken_throwsInvalidCredentials() {
		when(jwtTokenService.verify("access-token")).thenReturn(new JwtTokenService.TokenClaims(1L, "runner@example.com", "홍길동", "access", 0L));

		assertThatThrownBy(() -> authService.refresh("access-token"))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void refresh_withInvalidToken_throwsInvalidCredentials() {
		when(jwtTokenService.verify("bad-token")).thenThrow(new IllegalArgumentException("bad"));

		assertThatThrownBy(() -> authService.refresh("bad-token"))
				.isInstanceOf(InvalidCredentialsException.class);
	}
}
