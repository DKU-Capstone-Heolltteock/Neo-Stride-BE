package com.neostride.server.audit.service;

import com.neostride.server.audit.dto.AuditLogResponse;
import com.neostride.server.audit.repository.AuditLogRepository;
import java.util.List;
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

	public List<AuditLogResponse> search(String action, String targetType, String targetId, Long actorOperatorAccountId, int limit) {
		return repository.search(action, targetType, targetId, actorOperatorAccountId, limit);
	}

	public AuditLogResponse get(long auditLogId) {
		return repository.find(auditLogId)
				.orElseThrow(() -> new IllegalArgumentException("감사 로그를 찾을 수 없습니다."));
	}
}
