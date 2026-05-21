package com.neostride.server.notification.dto;

public record NotificationResponse(
		Long notificationId,
		String type,
		String message,
		String createdAt,
		Long targetId,
		boolean read
) {}
