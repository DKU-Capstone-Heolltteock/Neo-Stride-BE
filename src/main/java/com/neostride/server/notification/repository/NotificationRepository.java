package com.neostride.server.notification.repository;

import com.neostride.server.notification.dto.NotificationResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
	private final JdbcTemplate jdbcTemplate;

	public NotificationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<NotificationResponse> getNotifications(long userId) {
		// A dedicated notification table is not guaranteed in the current schema.
		// Return a stable empty list until durable notification persistence is added.
		return List.of();
	}

	public void deleteNotification(long notificationId) {
		// No-op compatibility endpoint. Kept idempotent for Android delete calls.
	}

	public void deleteAllNotifications(long userId) {
		// No-op compatibility endpoint. Kept idempotent for Android bulk delete calls.
	}
}
