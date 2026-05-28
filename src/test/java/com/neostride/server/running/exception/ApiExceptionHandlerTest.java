package com.neostride.server.running.exception;

import com.neostride.server.auth.dto.SignupResponse;
import com.neostride.server.auth.exception.DuplicateUserFieldException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void duplicateNicknameOnSignup_returnsConflictSignupResponse() {
		var request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/auth/signup"));

		var response = handler.handleDuplicateUserField(
				new DuplicateUserFieldException(DuplicateUserFieldException.Field.NICKNAME, "이미 사용 중인 닉네임입니다."),
				request
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isEqualTo(SignupResponse.error("이미 사용 중인 닉네임입니다."));
	}

	@Test
	void duplicateNicknameOnCommunityProfile_returnsConflictErrorMap() {
		var request = new ServletWebRequest(new MockHttpServletRequest("PATCH", "/api/community/users/me/nickname"));

		var response = handler.handleDuplicateUserField(
				new DuplicateUserFieldException(DuplicateUserFieldException.Field.NICKNAME, "이미 사용 중인 닉네임입니다."),
				request
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isEqualTo(Map.of("status", "error", "message", "이미 사용 중인 닉네임입니다."));
	}
}
