package com.neostride.server.auth.controller;

import com.neostride.server.auth.dto.LoginRequest;
import com.neostride.server.auth.dto.LoginResponse;
import com.neostride.server.auth.dto.SignupRequest;
import com.neostride.server.auth.dto.SignupResponse;
import com.neostride.server.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

	private final AuthService authService = mock(AuthService.class);
	private final AuthController controller = new AuthController(authService);

	@Test
	void signup_returnsCreatedResponse() {
		SignupRequest request = new SignupRequest("runner@example.com", "홍길동", "plain-password");
		when(authService.signup(request)).thenReturn(SignupResponse.success(1L, "runner@example.com", "홍길동"));

		var response = controller.signup(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo("success");
		assertThat(response.getBody().userId()).isEqualTo(1L);
		assertThat(response.getBody().email()).isEqualTo("runner@example.com");
		assertThat(response.getBody().name()).isEqualTo("홍길동");
	}

	@Test
	void login_returnsOkResponseWithTokens() {
		LoginRequest request = new LoginRequest("runner@example.com", "plain-password");
		when(authService.login(request)).thenReturn(LoginResponse.success(
				1L,
				"runner@example.com",
				"홍길동",
				"access-token",
				"refresh-token"
		));

		var response = controller.login(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo("success");
		assertThat(response.getBody().userId()).isEqualTo(1L);
		assertThat(response.getBody().accessToken()).isEqualTo("access-token");
		assertThat(response.getBody().refreshToken()).isEqualTo("refresh-token");
	}
}
