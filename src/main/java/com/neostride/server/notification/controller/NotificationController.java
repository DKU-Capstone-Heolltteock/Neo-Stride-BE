package com.neostride.server.notification.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.notification.dto.NotificationResponse;
import com.neostride.server.notification.service.NotificationService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {
	private final NotificationService service;
	private final AuthenticatedUserService authenticatedUserService;

	public NotificationController(NotificationService service, AuthenticatedUserService authenticatedUserService) {
		this.service = service;
		this.authenticatedUserService = authenticatedUserService;
	}

	@GetMapping("/api/notifications")
	public ResponseEntity<List<NotificationResponse>> getNotifications(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		long userId = authenticatedUserId(authorization, headerUserId);
		return ResponseEntity.ok(service.getNotifications(userId));
	}

	@PatchMapping("/api/notifications/{notificationId}/read")
	public ResponseEntity<Void> markRead(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable Long notificationId
	) {
		service.markRead(authenticatedUserId(authorization, null), notificationId);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/api/notifications/read-all")
	public ResponseEntity<Void> markAllRead(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		long userId = authenticatedUserId(authorization, headerUserId);
		service.markAllRead(userId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/notifications/{notificationId}")
	public ResponseEntity<Void> deleteNotification(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable Long notificationId
	) {
		service.deleteNotification(authenticatedUserId(authorization, null), notificationId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/notifications")
	public ResponseEntity<Void> deleteAllNotifications(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		long userId = authenticatedUserId(authorization, headerUserId);
		service.deleteAllNotifications(userId);
		return ResponseEntity.noContent().build();
	}

	private long authenticatedUserId(String authorization, Long headerUserId) {
		long authenticatedUserId = authenticatedUserService.requireUserId(authorization);
		authenticatedUserService.requireSameUserIfPresent(authenticatedUserId, headerUserId, "X-User-Id");
		return authenticatedUserId;
	}
}
