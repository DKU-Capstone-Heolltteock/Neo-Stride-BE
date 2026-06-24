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
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminNotificationService {
	private final BroadcastRepository broadcastRepository;
	private final UserAdministrationPort userAdministrationPort;
	private final NotificationSender notificationSender;
	private final DiscordWebhookClient discordWebhookClient;
	private final AuditLogService auditLogService;
	private final int maxBroadcastRecipients;

	public AdminNotificationService(
			BroadcastRepository broadcastRepository,
			UserAdministrationPort userAdministrationPort,
			NotificationSender notificationSender,
			DiscordWebhookClient discordWebhookClient,
			AuditLogService auditLogService,
			@Value("${neostride.admin.broadcast.max-recipients:${ADMIN_BROADCAST_MAX_RECIPIENTS:1000}}") int maxBroadcastRecipients
	) {
		this.broadcastRepository = broadcastRepository;
		this.userAdministrationPort = userAdministrationPort;
		this.notificationSender = notificationSender;
		this.discordWebhookClient = discordWebhookClient;
		this.auditLogService = auditLogService;
		this.maxBroadcastRecipients = Math.max(1, maxBroadcastRecipients);
	}

	@Transactional
	public BroadcastResponse broadcast(BroadcastRequest request, OperatorPrincipal actor, AuditContext context) {
		validateBroadcast(request);
		String targetType = normalizeTargetType(request.targetType());
		List<Long> recipients = recipients(targetType, request.targetUserId());
		long expectedRecipients = expectedRecipients(targetType, recipients.size());
		int sent = 0;
		for (Long userId : recipients) {
			notificationSender.send(userId, "OPERATOR_BROADCAST", request.message().trim(), "/notifications");
			sent++;
		}
		DiscordWebhookClient.DiscordWebhookResult discordResult = discordWebhookClient.send(
				"[Neo-Stride 운영자 알림] " + request.title().trim() + "\n" + request.message().trim()
		);
		String status = sent == expectedRecipients ? "SENT" : "PARTIAL";
		BroadcastResponse response = broadcastRepository.insert(
				actor.operatorAccountId(),
				request.title().trim(),
				request.message().trim(),
				targetType,
				request.targetUserId(),
				sent,
				status,
				discordResult.status(),
				discordResult.error(),
				request.reason().trim()
		);
		auditLogService.record(actor.operatorAccountId(), "notification.broadcast", "operator_broadcast",
				String.valueOf(response.broadcastId()), request.reason().trim(), null, status, context);
		return response;
	}

	public List<BroadcastResponse> list(int limit) {
		return broadcastRepository.list(limit);
	}

	public BroadcastResponse get(long broadcastId) {
		return broadcastRepository.find(broadcastId)
				.orElseThrow(() -> new IllegalArgumentException("방송 알림을 찾을 수 없습니다."));
	}

	private void validateBroadcast(BroadcastRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		if (request.title() == null || request.title().isBlank()) {
			throw new IllegalArgumentException("title은 필수입니다.");
		}
		if (request.message() == null || request.message().isBlank()) {
			throw new IllegalArgumentException("message는 필수입니다.");
		}
		if (request.reason() == null || request.reason().isBlank()) {
			throw new IllegalArgumentException("reason은 필수입니다.");
		}
	}

	private List<Long> recipients(String targetType, Long targetUserId) {
		if ("USER".equals(targetType)) {
			if (targetUserId == null || targetUserId <= 0) {
				throw new IllegalArgumentException("target_user_id는 1 이상의 값이어야 합니다.");
			}
			return List.of(targetUserId);
		}
		return userAdministrationPort.activeUserIds(maxBroadcastRecipients);
	}

	private long expectedRecipients(String targetType, int selectedRecipients) {
		if ("ALL".equals(targetType)) {
			return userAdministrationPort.countAccounts("ACTIVE");
		}
		return selectedRecipients;
	}

	private String normalizeTargetType(String value) {
		if (value == null || value.isBlank()) {
			return "ALL";
		}
		String normalized = value.trim().toUpperCase();
		if (!List.of("ALL", "USER").contains(normalized)) {
			throw new IllegalArgumentException("target_type은 ALL 또는 USER만 가능합니다.");
		}
		return normalized;
	}
}
