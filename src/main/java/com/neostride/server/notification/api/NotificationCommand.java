package com.neostride.server.notification.api;

public record NotificationCommand(
		long userId,
		String type,
		String message,
		String endpoint
) {}
