package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OperatorLoginRequest(
		@JsonProperty("email")
		String email,

		@JsonProperty("password")
		String password
) {}
