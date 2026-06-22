package com.neostride.server.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UsageMetricResponse(
		@JsonProperty("total_users")
		long totalUsers,
		@JsonProperty("active_users")
		long activeUsers,
		@JsonProperty("suspended_users")
		long suspendedUsers,
		@JsonProperty("requests_last_24h")
		long requestsLast24h,
		@JsonProperty("errors_last_24h")
		long errorsLast24h
) {}
