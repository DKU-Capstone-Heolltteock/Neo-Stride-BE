package com.neostride.server.auth.service;

import com.neostride.server.auth.dto.LoginRequest;
import com.neostride.server.auth.dto.LoginResponse;
import com.neostride.server.auth.exception.InvalidCredentialsException;
import com.neostride.server.auth.repository.UserRepository;
import com.neostride.server.auth.repository.UserRow;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
	void login_withWrongPassword_throwsInvalidCredentials() {
		String storedHash = "pbkdf2_sha256$hash";
		when(userRepository.findByEmail("runner@example.com"))
				.thenReturn(Optional.of(new UserRow(1L, "runner@example.com", "홍길동", storedHash)));
		when(passwordHashService.matches("wrong-password", storedHash)).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest("runner@example.com", "wrong-password")))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
	}
}
