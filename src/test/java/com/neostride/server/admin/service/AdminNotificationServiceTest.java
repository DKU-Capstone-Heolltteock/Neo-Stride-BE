package com.neostride.server.admin.service;

import com.neostride.server.admin.dto.BroadcastRequest;
import com.neostride.server.admin.dto.BroadcastResponse;
import com.neostride.server.admin.repository.BroadcastRepository;
import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.auth.api.UserAdministrationPort;
import com.neostride.server.notification.api.NotificationSender;
import com.neostride.server.ops.service.DiscordWebhookClient;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminNotificationServiceTest {
	private final BroadcastRepository broadcastRepository = mock(BroadcastRepository.class);
	private final UserAdministrationPort userAdministrationPort = mock(UserAdministrationPort.class);
	private final NotificationSender notificationSender = mock(NotificationSender.class);
	private final DiscordWebhookClient discordWebhookClient = mock(DiscordWebhookClient.class);
	private final AuditLogService auditLogService = mock(AuditLogService.class);

	@Test
	void allUserBroadcastIsPartialWhenActiveUsersExceedSelectedRecipients() {
		AdminNotificationService service = new AdminNotificationService(
				broadcastRepository,
				userAdministrationPort,
				notificationSender,
				discordWebhookClient,
				auditLogService,
				2
		);
		OperatorPrincipal actor = new OperatorPrincipal(7L, "ops@example.com", "Ops", "OPERATOR_ADMIN", List.of("notification:send"));
		BroadcastRequest request = new BroadcastRequest("notice", "message", "ALL", null, "maintenance");
		BroadcastResponse stored = response("PARTIAL", 2);

		when(userAdministrationPort.activeUserIds(2)).thenReturn(List.of(11L, 12L));
		when(userAdministrationPort.countAccounts("ACTIVE")).thenReturn(3L);
		when(discordWebhookClient.send(any())).thenReturn(new DiscordWebhookClient.DiscordWebhookResult("SENT", null));
		when(broadcastRepository.insert(eq(7L), eq("notice"), eq("message"), eq("ALL"), eq(null), eq(2), eq("PARTIAL"),
				eq("SENT"), eq(null), eq("maintenance"))).thenReturn(stored);

		BroadcastResponse response = service.broadcast(request, actor, new AuditContext(null, null, null));

		assertThat(response.status()).isEqualTo("PARTIAL");
		verify(notificationSender).send(11L, "OPERATOR_BROADCAST", "message", "/notifications");
		verify(notificationSender).send(12L, "OPERATOR_BROADCAST", "message", "/notifications");
		verify(auditLogService).record(eq(7L), eq("notification.broadcast"), eq("operator_broadcast"), eq("33"),
				eq("maintenance"), eq(null), eq("PARTIAL"), any(AuditContext.class));
	}

	private BroadcastResponse response(String status, int recipientCount) {
		return new BroadcastResponse(
				33L,
				7L,
				"notice",
				"message",
				"ALL",
				null,
				recipientCount,
				status,
				"SENT",
				null,
				"maintenance",
				LocalDateTime.parse("2026-06-24T00:00:00")
		);
	}
}
