package com.neostride.server.notification;

import com.neostride.server.notification.controller.NotificationController;
import com.neostride.server.notification.dto.NotificationResponse;
import com.neostride.server.notification.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationControllerTest {

	private final NotificationService service = mock(NotificationService.class);
	private final NotificationController controller = new NotificationController(service);

	@Test
	void notificationApis_supportAndroidContracts() {
		NotificationResponse notification = new NotificationResponse(1L, "LIKE", "좋아요가 추가되었습니다.", "2026-05-21T00:00:00", 99L, false);
		when(service.getNotifications(1L)).thenReturn(List.of(notification));

		assertThat(controller.getNotifications(1L).getBody()).containsExactly(notification);
		assertThat(controller.deleteNotification(1L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(controller.deleteAllNotifications(1L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(service).deleteNotification(1L);
		verify(service).deleteAllNotifications(1L);
	}
}
