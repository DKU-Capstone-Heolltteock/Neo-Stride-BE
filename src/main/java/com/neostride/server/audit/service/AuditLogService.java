package com.neostride.server.audit.service;

import com.neostride.server.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
	private final AuditLogRepository repository;

	public AuditLogService(AuditLogRepository repository) {
		this.repository = repository;
	}

	public void record(
			Long actorOperatorAccountId,
			String action,
			String targetType,
			String targetId,
			String reason,
			String beforeSummary,
			String afterSummary,
			AuditContext context
	) {
		repository.insert(actorOperatorAccountId, action, targetType, targetId, reason, beforeSummary, afterSummary, context);
	}
}
