package com.neostride.server.notification.service;

import com.neostride.server.notification.api.NotificationSender;
import com.neostride.server.platform.event.NotificationRequestedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {
	private final NotificationSender notificationSender;

	public NotificationEventListener(NotificationSender notificationSender) {
		this.notificationSender = notificationSender;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void handle(NotificationRequestedEvent event) {
		if (event == null) {
			return;
		}
		notificationSender.send(event.userId(), event.type(), event.message(), event.endpoint());
	}
}
