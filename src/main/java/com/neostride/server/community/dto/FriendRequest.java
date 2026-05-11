package com.neostride.server.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FriendRequest(
		@JsonProperty("target_id") Long targetId,
		String action
) {}
