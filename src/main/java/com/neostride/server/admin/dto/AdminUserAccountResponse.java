package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.neostride.server.auth.api.AdminUserAccount;
import java.time.LocalDateTime;

public record AdminUserAccountResponse(
		@JsonProperty("user_id")
		long userId,
		@JsonProperty("email")
		String email,
		@JsonProperty("name")
		String name,
		@JsonProperty("nickname")
		String nickname,
		@JsonProperty("profile_image_url")
		String profileImageUrl,
		@JsonProperty("status")
		String status,
		@JsonProperty("suspended_at")
		LocalDateTime suspendedAt,
		@JsonProperty("suspended_until")
		LocalDateTime suspendedUntil,
		@JsonProperty("suspended_reason")
		String suspendedReason,
		@JsonProperty("deleted_at")
		LocalDateTime deletedAt,
		@JsonProperty("created_at")
		LocalDateTime createdAt,
		@JsonProperty("updated_at")
		LocalDateTime updatedAt
) {
	public static AdminUserAccountResponse from(AdminUserAccount account) {
		return new AdminUserAccountResponse(
				account.userId(),
				account.email(),
				account.name(),
				account.nickname(),
				account.profileImageUrl(),
				account.status(),
				account.suspendedAt(),
				account.suspendedUntil(),
				account.suspendedReason(),
				account.deletedAt(),
				account.createdAt(),
				account.updatedAt()
		);
	}
}
