package com.neostride.server.audit.repository;

import com.neostride.server.audit.dto.AuditLogResponse;
import com.neostride.server.audit.service.AuditContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

	public List<AuditLogResponse> search(String action, String targetType, String targetId, Long actorOperatorAccountId, int limit) {
		List<Object> args = new ArrayList<>();
		StringBuilder sql = new StringBuilder("""
				SELECT operator_audit_log_id, actor_operator_account_id, action, target_type, target_id,
				       reason, before_summary, after_summary, request_id, ip_address, user_agent, created_at
				FROM operator_audit_logs
				WHERE 1 = 1
				""");
		if (action != null && !action.isBlank()) {
			sql.append(" AND action = ?");
			args.add(action.trim());
		}
		if (targetType != null && !targetType.isBlank()) {
			sql.append(" AND target_type = ?");
			args.add(targetType.trim());
		}
		if (targetId != null && !targetId.isBlank()) {
			sql.append(" AND target_id = ?");
			args.add(targetId.trim());
		}
		if (actorOperatorAccountId != null) {
			sql.append(" AND actor_operator_account_id = ?");
			args.add(actorOperatorAccountId);
		}
		sql.append(" ORDER BY created_at DESC, operator_audit_log_id DESC LIMIT ?");
		args.add(cappedLimit(limit));
		return jdbcTemplate.query(sql.toString(), this::mapLog, args.toArray());
	}

	public Optional<AuditLogResponse> find(long auditLogId) {
		List<AuditLogResponse> rows = jdbcTemplate.query("""
				SELECT operator_audit_log_id, actor_operator_account_id, action, target_type, target_id,
				       reason, before_summary, after_summary, request_id, ip_address, user_agent, created_at
				FROM operator_audit_logs
				WHERE operator_audit_log_id = ?
				""", this::mapLog, auditLogId);
		return rows.stream().findFirst();
	}

	private AuditLogResponse mapLog(ResultSet rs, int rowNum) throws SQLException {
		long actorId = rs.getLong("actor_operator_account_id");
		Long actorOperatorAccountId = rs.wasNull() ? null : actorId;
		return new AuditLogResponse(
				rs.getLong("operator_audit_log_id"),
				actorOperatorAccountId,
				rs.getString("action"),
				rs.getString("target_type"),
				rs.getString("target_id"),
				rs.getString("reason"),
				rs.getString("before_summary"),
				rs.getString("after_summary"),
				rs.getString("request_id"),
				rs.getString("ip_address"),
				rs.getString("user_agent"),
				toLocalDateTime(rs, "created_at")
		);
	}

	private int cappedLimit(int limit) {
		return Math.min(Math.max(limit, 1), 200);
	}

	private LocalDateTime toLocalDateTime(ResultSet rs, String columnName) throws SQLException {
		var timestamp = rs.getTimestamp(columnName);
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}
}
