package com.neostride.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "회원가입 응답")
public record SignupResponse(
		@Schema(description = "처리 결과", example = "success", allowableValues = {"success", "error"})
		@JsonProperty("status")
		String status,

		@Schema(description = "처리 메시지", example = "회원가입이 완료되었습니다.")
		@JsonProperty("message")
		String message,

		@Schema(description = "생성된 사용자 ID", example = "1")
		@JsonProperty("user_id")
		Long userId,

		@Schema(description = "사용자 이메일 주소", example = "runner@example.com")
		@JsonProperty("email")
		String email,

		@Schema(description = "사용자 이름", example = "홍길동")
		@JsonProperty("name")
		String name
) {
	public static SignupResponse success(long userId, String email, String name) {
		return new SignupResponse("success", "회원가입이 완료되었습니다.", userId, email, name);
	}

	public static SignupResponse error(String message) {
		return new SignupResponse("error", message, null, null, null);
	}
}
