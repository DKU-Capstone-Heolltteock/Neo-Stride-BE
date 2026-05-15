package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccountInfoResponse(
		String email,
		String nickname,
		@JsonProperty("profile_photo") String profilePhoto
) {}
