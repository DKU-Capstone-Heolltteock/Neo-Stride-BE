package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OperatorRefreshRequest(
		@JsonProperty("refresh_token")
		String refreshToken
) {}
