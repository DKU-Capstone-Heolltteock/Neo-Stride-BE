package com.neostride.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 요청")
public record SignupRequest(
		@Schema(description = "사용자 이메일 주소", example = "runner@example.com")
		@JsonProperty("email")
		String email,

		@Schema(description = "사용자 이름", example = "홍길동")
		@JsonProperty("name")
		String name,

		@Schema(description = "평문 비밀번호. 서버에서 해시 후 저장합니다.", example = "plain-password")
		@JsonProperty("password")
		String password
) {
}
