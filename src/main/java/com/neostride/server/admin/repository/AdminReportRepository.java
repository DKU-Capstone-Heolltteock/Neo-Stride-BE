package com.neostride.server.admin.repository;

import com.neostride.server.admin.dto.AdminReportResponse;
import com.neostride.server.platform.web.CursorSupport;
import com.neostride.server.platform.web.CursorSupport.CursorPosition;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminReportRepository {
	private final JdbcTemplate jdbcTemplate;

	public AdminReportRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<AdminReportResponse> list(String status, int limit) {
		return list(status, null, null, null, limit);
	}

	public List<AdminReportResponse> list(String status, CursorPosition cursor, LocalDateTime from, LocalDateTime to, int limit) {
		List<Object> args = new ArrayList<>();
		StringBuilder sql = new StringBuilder("""
				SELECT report_id, reporter_user_id, target_user_id, target_type, target_id, category,
				       status, reason, resolution, assigned_operator_account_id, resolved_at, created_at, updated_at
				FROM admin_reports
				WHERE 1 = 1
				""");
		if (status != null && !status.isBlank()) {
			sql.append(" AND status = ?");
			args.add(normalizeStatus(status));
		}
		appendRangeAndCursor(sql, args, cursor, from, to);
		sql.append(" ORDER BY created_at DESC, report_id DESC LIMIT ?");
		args.add(CursorSupport.cappedFetchLimit(limit));
		return jdbcTemplate.query(sql.toString(), this::mapReport, args.toArray());
	}

	public Optional<AdminReportResponse> find(long reportId) {
		List<AdminReportResponse> rows = jdbcTemplate.query("""
				SELECT report_id, reporter_user_id, target_user_id, target_type, target_id, category,
				       status, reason, resolution, assigned_operator_account_id, resolved_at, created_at, updated_at
				FROM admin_reports
				WHERE report_id = ?
				""", this::mapReport, reportId);
		return rows.stream().findFirst();
	}

	public AdminReportResponse resolve(long reportId, String status, String resolution, long operatorAccountId) {
		int updated = jdbcTemplate.update("""
				UPDATE admin_reports
				SET status = ?,
				    resolution = ?,
				    assigned_operator_account_id = ?,
				    resolved_at = NOW()
				WHERE report_id = ?
				""", normalizeResolveStatus(status), resolution, operatorAccountId, reportId);
		if (updated == 0) {
			throw new IllegalArgumentException("신고를 찾을 수 없습니다.");
		}
		return find(reportId).orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없습니다."));
	}

	public AdminReportResponse assign(long reportId, Long operatorAccountId) {
		int updated = jdbcTemplate.update("""
				UPDATE admin_reports
				SET assigned_operator_account_id = ?
				WHERE report_id = ?
				""", operatorAccountId, reportId);
		if (updated == 0) {
			throw new IllegalArgumentException("신고를 찾을 수 없습니다.");
		}
		return find(reportId).orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없습니다."));
	}

	private AdminReportResponse mapReport(ResultSet rs, int rowNum) throws SQLException {
		return new AdminReportResponse(
				rs.getLong("report_id"),
				nullableLong(rs, "reporter_user_id"),
				nullableLong(rs, "target_user_id"),
				rs.getString("target_type"),
				rs.getString("target_id"),
				rs.getString("category"),
				rs.getString("status"),
				rs.getString("reason"),
				rs.getString("resolution"),
				nullableLong(rs, "assigned_operator_account_id"),
				toLocalDateTime(rs, "resolved_at"),
				toLocalDateTime(rs, "created_at"),
				toLocalDateTime(rs, "updated_at")
		);
	}

	private String normalizeStatus(String status) {
		String normalized = status.trim().toUpperCase();
		if (!List.of("PENDING", "RESOLVED", "REJECTED").contains(normalized)) {
			throw new IllegalArgumentException("status는 PENDING, RESOLVED, REJECTED만 가능합니다.");
		}
		return normalized;
	}

	private String normalizeResolveStatus(String status) {
		if (status == null || status.isBlank()) {
			return "RESOLVED";
		}
		String normalized = status.trim().toUpperCase();
		if (!List.of("RESOLVED", "REJECTED").contains(normalized)) {
			throw new IllegalArgumentException("처리 status는 RESOLVED 또는 REJECTED만 가능합니다.");
		}
		return normalized;
	}

	private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
		long value = rs.getLong(columnName);
		return rs.wasNull() ? null : value;
	}

	private LocalDateTime toLocalDateTime(ResultSet rs, String columnName) throws SQLException {
		var timestamp = rs.getTimestamp(columnName);
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}

	private void appendRangeAndCursor(StringBuilder sql, List<Object> args, CursorPosition cursor, LocalDateTime from, LocalDateTime to) {
		if (from != null) {
			sql.append(" AND created_at >= ?");
			args.add(from);
		}
		if (to != null) {
			sql.append(" AND created_at <= ?");
			args.add(to);
		}
		if (cursor != null) {
			sql.append(" AND (created_at < ? OR (created_at = ? AND report_id < ?))");
			args.add(cursor.createdAt());
			args.add(cursor.createdAt());
			args.add(cursor.id());
		}
	}
}
