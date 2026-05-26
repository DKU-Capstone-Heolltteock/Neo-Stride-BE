package com.neostride.server.notification.controller;

import com.neostride.server.notification.dto.NotificationResponse;
import com.neostride.server.notification.service.NotificationService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {
	private final NotificationService service;

	public NotificationController(NotificationService service) {
		this.service = service;
	}

	@GetMapping("/api/notifications")
	public ResponseEntity<List<NotificationResponse>> getNotifications(
			@RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId
	) {
		return ResponseEntity.ok(service.getNotifications(userId));
	}

	@PatchMapping("/api/notifications/{notificationId}/read")
	public ResponseEntity<Void> markRead(@PathVariable Long notificationId) {
		service.markRead(notificationId);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/api/notifications/read-all")
	public ResponseEntity<Void> markAllRead(
			@RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId
	) {
		service.markAllRead(userId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/notifications/{notificationId}")
	public ResponseEntity<Void> deleteNotification(@PathVariable Long notificationId) {
		service.deleteNotification(notificationId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/notifications")
	public ResponseEntity<Void> deleteAllNotifications(
			@RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId
	) {
		service.deleteAllNotifications(userId);
		return ResponseEntity.noContent().build();
	}
}
