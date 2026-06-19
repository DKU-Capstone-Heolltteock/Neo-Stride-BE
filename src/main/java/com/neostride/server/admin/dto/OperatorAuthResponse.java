package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperatorAuthResponse(
		@JsonProperty("status")
		String status,

		@JsonProperty("message")
		String message,

		@JsonProperty("operator_account_id")
		Long operatorAccountId,

		@JsonProperty("email")
		String email,

		@JsonProperty("name")
		String name,

		@JsonProperty("role")
		String role,

		@JsonProperty("permissions")
		List<String> permissions,

		@JsonProperty("access_token")
		String accessToken,

		@JsonProperty("refresh_token")
		String refreshToken
) {
	public static OperatorAuthResponse success(
			long operatorAccountId,
			String email,
			String name,
			String role,
			List<String> permissions,
			String accessToken,
			String refreshToken
	) {
		return new OperatorAuthResponse(
				"success",
				"관리자 로그인에 성공했습니다.",
				operatorAccountId,
				email,
				name,
				role,
				permissions,
				accessToken,
				refreshToken
		);
	}

	public static OperatorAuthResponse me(long operatorAccountId, String email, String name, String role, List<String> permissions) {
		return new OperatorAuthResponse("success", null, operatorAccountId, email, name, role, permissions, null, null);
	}

	public static OperatorAuthResponse ok(String message) {
		return new OperatorAuthResponse("success", message, null, null, null, null, null, null, null);
	}
}
