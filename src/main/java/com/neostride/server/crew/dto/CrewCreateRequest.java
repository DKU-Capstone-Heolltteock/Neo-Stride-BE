package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewCreateRequest(
		String name,
		String description,
		String visibility,
		@JsonProperty("join_policy") @JsonAlias("joinPolicy") String joinPolicy,
		String region,
		@JsonProperty("profile_image_url") @JsonAlias("profileImageUrl") String profileImageUrl
) {}
