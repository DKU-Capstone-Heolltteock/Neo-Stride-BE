package com.neostride.server.auth.api;

import java.time.LocalDateTime;

public record AdminUserAccount(
		long userId,
		String email,
		String name,
		String nickname,
		String profileImageUrl,
		String status,
		LocalDateTime suspendedAt,
		LocalDateTime suspendedUntil,
		String suspendedReason,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {}
