package com.neostride.server.notification.service;

import com.neostride.server.notification.api.NotificationCommand;
import com.neostride.server.notification.api.NotificationSender;
import com.neostride.server.notification.dto.NotificationResponse;
import com.neostride.server.notification.repository.NotificationRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NotificationService implements NotificationSender {
	private final NotificationRepository repository;

	public NotificationService(NotificationRepository repository) {
		this.repository = repository;
	}

	public List<NotificationResponse> getNotifications(long userId) {
		validatePositive(userId, "user_id");
		return repository.getNotifications(userId);
	}

	public void markRead(long userId, long notificationId) {
		validatePositive(userId, "user_id");
		validatePositive(notificationId, "notification_id");
		repository.markRead(userId, notificationId);
	}

	public void markAllRead(long userId) {
		validatePositive(userId, "user_id");
		repository.markAllRead(userId);
	}

	public void deleteNotification(long userId, long notificationId) {
		validatePositive(userId, "user_id");
		validatePositive(notificationId, "notification_id");
		repository.deleteNotification(userId, notificationId);
	}

	public void deleteAllNotifications(long userId) {
		validatePositive(userId, "user_id");
		repository.deleteAllNotifications(userId);
	}

	@Override
	public void send(NotificationCommand command) {
		if (command == null) {
			return;
		}
		repository.createNotification(command.userId(), command.type(), command.message(), command.endpoint());
	}

	private void validatePositive(long value, String fieldName) {
		if (value <= 0) throw new IllegalArgumentException(fieldName + "는 1 이상의 값이어야 합니다.");
	}
}
