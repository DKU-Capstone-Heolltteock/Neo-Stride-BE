package com.neostride.server.audit.repository;

import com.neostride.server.audit.service.AuditContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {
	private final JdbcTemplate jdbcTemplate;

	public AuditLogRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void insert(
			Long actorOperatorAccountId,
			String action,
			String targetType,
			String targetId,
			String reason,
			String beforeSummary,
			String afterSummary,
			AuditContext context
	) {
		jdbcTemplate.update("""
				INSERT INTO operator_audit_logs (
				    actor_operator_account_id, action, target_type, target_id, reason,
				    before_summary, after_summary, request_id, ip_address, user_agent
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				actorOperatorAccountId,
				action,
				targetType,
				targetId,
				reason,
				beforeSummary,
				afterSummary,
				context == null ? null : context.requestId(),
				context == null ? null : context.ipAddress(),
				context == null ? null : context.userAgent()
		);
	}
}
