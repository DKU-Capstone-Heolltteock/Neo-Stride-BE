package com.neostride.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "로그인 응답")
public record LoginResponse(
		@Schema(description = "처리 결과", example = "success", allowableValues = {"success", "error"})
		@JsonProperty("status")
		String status,

		@Schema(description = "처리 메시지", example = "로그인에 성공했습니다.")
		@JsonProperty("message")
		String message,

		@Schema(description = "사용자 ID", example = "1")
		@JsonProperty("user_id")
		Long userId,

		@Schema(description = "사용자 이메일 주소", example = "runner@example.com")
		@JsonProperty("email")
		String email,

		@Schema(description = "사용자 이름", example = "홍길동")
		@JsonProperty("name")
		String name,

		@Schema(description = "프론트엔드 TokenManager 호환용 닉네임", example = "홍길동")
		@JsonProperty("nickname")
		String nickname,

		@Schema(description = "JWT access token")
		@JsonProperty("access_token")
		String accessToken,

		@Schema(description = "JWT refresh token")
		@JsonProperty("refresh_token")
		String refreshToken
) {
	public static LoginResponse success(long userId, String email, String name, String accessToken, String refreshToken) {
		return new LoginResponse("success", "로그인에 성공했습니다.", userId, email, name, name, accessToken, refreshToken);
	}

	public static LoginResponse error(String message) {
		return new LoginResponse("error", message, null, null, null, null, null, null);
	}
}
