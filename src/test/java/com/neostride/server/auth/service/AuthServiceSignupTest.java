package com.neostride.server.auth.service;

import com.neostride.server.auth.dto.SignupRequest;
import com.neostride.server.auth.dto.SignupResponse;
import com.neostride.server.auth.exception.DuplicateUserFieldException;
import com.neostride.server.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceSignupTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final PasswordHashService passwordHashService = mock(PasswordHashService.class);
	private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
	private final AuthService authService = new AuthService(userRepository, passwordHashService, jwtTokenService);

	@Test
	void signup_withUniqueEmailNameAndNickname_createsUser() {
		SignupRequest request = new SignupRequest("Runner@Example.com", "홍길동", "plain-password");
		when(userRepository.existsByEmail("runner@example.com")).thenReturn(false);
		when(userRepository.existsByName("홍길동")).thenReturn(false);
		when(userRepository.existsByCommunityProfileName("홍길동")).thenReturn(false);
		when(passwordHashService.hash("plain-password")).thenReturn("hashed-password");
		when(userRepository.insertUser("runner@example.com", "hashed-password", "홍길동", null)).thenReturn(1L);

		SignupResponse response = authService.signup(request);

		assertThat(response.status()).isEqualTo("success");
		assertThat(response.userId()).isEqualTo(1L);
		assertThat(response.email()).isEqualTo("runner@example.com");
		assertThat(response.name()).isEqualTo("홍길동");
		verify(userRepository).insertUser("runner@example.com", "hashed-password", "홍길동", null);
	}

	@Test
	void signup_whenNameAlreadyExists_throwsDuplicateUserFieldException() {
		SignupRequest request = new SignupRequest("runner@example.com", "홍길동", "plain-password");
		when(userRepository.existsByEmail("runner@example.com")).thenReturn(false);
		when(userRepository.existsByName("홍길동")).thenReturn(true);

		assertThatThrownBy(() -> authService.signup(request))
				.isInstanceOf(DuplicateUserFieldException.class)
				.hasMessage("이미 사용 중인 사용자 이름입니다.");
	}

	@Test
	void signup_whenNicknameAlreadyExists_throwsDuplicateUserFieldException() {
		SignupRequest request = new SignupRequest("runner@example.com", "홍길동", "plain-password");
		when(userRepository.existsByEmail("runner@example.com")).thenReturn(false);
		when(userRepository.existsByName("홍길동")).thenReturn(false);
		when(userRepository.existsByCommunityProfileName("홍길동")).thenReturn(true);

		assertThatThrownBy(() -> authService.signup(request))
				.isInstanceOf(DuplicateUserFieldException.class)
				.hasMessage("이미 사용 중인 닉네임입니다.");
	}
}
