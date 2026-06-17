package com.neostride.server.platform.event;

public record NotificationRequestedEvent(
		long userId,
		String type,
		String message,
		String endpoint
) {}
