package com.neostride.server.notification.api;

public interface NotificationSender {
	void send(NotificationCommand command);

	default void send(long userId, String type, String message, String endpoint) {
		send(new NotificationCommand(userId, type, message, endpoint));
	}
}
