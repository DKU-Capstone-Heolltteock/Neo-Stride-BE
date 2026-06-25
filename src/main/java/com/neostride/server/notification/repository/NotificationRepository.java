package com.neostride.server.notification.repository;

import com.neostride.server.notification.dto.NotificationResponse;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
	private static final Pattern TRAILING_ID = Pattern.compile("(\\d+)(?:\\D*)$");
	private final JdbcTemplate jdbcTemplate;

	public NotificationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<NotificationResponse> getNotifications(long userId) {
		return jdbcTemplate.query("""
			SELECT notification_id, notification_type, message, endpoint, is_read, created_at
			FROM notifications
			WHERE user_id = ?
			ORDER BY created_at DESC, notification_id DESC
			""", (rs, rowNum) -> new NotificationResponse(
				rs.getLong("notification_id"),
				rs.getString("notification_type"),
				rs.getString("message"),
				formatCreatedAt(rs.getTimestamp("created_at")),
				targetId(rs.getString("endpoint")),
				rs.getBoolean("is_read")
		), userId);
	}

	public void createNotification(long userId, String type, String message, String endpoint) {
		if (userId <= 0 || type == null || type.isBlank() || message == null || message.isBlank()) {
			return;
		}
		jdbcTemplate.update("""
			INSERT INTO notifications (user_id, notification_type, message, endpoint, is_read, created_at)
			SELECT ?, ?, ?, ?, FALSE, NOW()
			FROM users
			WHERE user_id = ? AND deleted_at IS NULL
			""", userId, type, message, endpoint, userId);
	}

	public void createNotificationIfAbsent(long userId, String type, String message, String endpoint) {
		if (userId <= 0 || type == null || type.isBlank() || message == null || message.isBlank()) {
			return;
		}
		jdbcTemplate.update("""
			INSERT INTO notifications (user_id, notification_type, message, endpoint, is_read, created_at)
			SELECT ?, ?, ?, ?, FALSE, NOW()
			FROM users
			WHERE user_id = ? AND deleted_at IS NULL
			  AND NOT EXISTS (
				SELECT 1 FROM notifications
				WHERE user_id = ? AND notification_type = ? AND endpoint <=> ?
			)
			""", userId, type, message, endpoint, userId, userId, type, endpoint);
	}

	public void markRead(long userId, long notificationId) {
		jdbcTemplate.update("UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND notification_id = ?",
				userId, notificationId);
	}

	public void markAllRead(long userId) {
		jdbcTemplate.update("UPDATE notifications SET is_read = TRUE WHERE user_id = ?", userId);
	}

	public void deleteNotification(long userId, long notificationId) {
		jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ? AND notification_id = ?",
				userId, notificationId);
	}

	public void deleteAllNotifications(long userId) {
		jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
	}

	private static String formatCreatedAt(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toLocalDateTime().format(ISO);
	}

	private static Long targetId(String endpoint) {
		if (endpoint == null || endpoint.isBlank()) {
			return null;
		}
		Matcher matcher = TRAILING_ID.matcher(endpoint);
		if (!matcher.find()) {
			return null;
		}
		try {
			return Long.parseLong(matcher.group(1));
		} catch (NumberFormatException exception) {
			return null;
		}
	}
}
