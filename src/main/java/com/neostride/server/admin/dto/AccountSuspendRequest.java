package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record AccountSuspendRequest(
		@JsonProperty("reason")
		String reason,
		@JsonProperty("suspended_until")
		LocalDateTime suspendedUntil
) {}
