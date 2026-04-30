package com.neostride.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 요청")
public record LoginRequest(
		@Schema(description = "사용자 이메일 주소", example = "runner@example.com")
		@JsonProperty("email")
		String email,

		@Schema(description = "평문 비밀번호", example = "plain-password")
		@JsonProperty("password")
		String password
) {
}
