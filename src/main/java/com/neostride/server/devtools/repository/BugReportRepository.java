package com.neostride.server.devtools.repository;

import com.neostride.server.devtools.dto.BugReportResponse;
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
public class BugReportRepository {
	private final JdbcTemplate jdbcTemplate;

	public BugReportRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<BugReportResponse> list(String status, int limit) {
		return list(status, null, null, null, limit);
	}

	public List<BugReportResponse> list(String status, CursorPosition cursor, LocalDateTime from, LocalDateTime to, int limit) {
		List<Object> args = new ArrayList<>();
		StringBuilder sql = new StringBuilder("""
				SELECT bug_report_id, reporter_user_id, title, description, status, severity, app_version, device_model, created_at, updated_at
				FROM bug_reports
				WHERE 1 = 1
				""");
		if (status != null && !status.isBlank()) {
			sql.append(" AND status = ?");
			args.add(normalizeStatus(status));
		}
		appendRangeAndCursor(sql, args, cursor, from, to);
		sql.append(" ORDER BY created_at DESC, bug_report_id DESC LIMIT ?");
		args.add(CursorSupport.cappedFetchLimit(limit));
		return jdbcTemplate.query(sql.toString(), this::mapReport, args.toArray());
	}

	public Optional<BugReportResponse> find(long bugReportId) {
		List<BugReportResponse> rows = jdbcTemplate.query("""
				SELECT bug_report_id, reporter_user_id, title, description, status, severity, app_version, device_model, created_at, updated_at
				FROM bug_reports
				WHERE bug_report_id = ?
				""", this::mapReport, bugReportId);
		return rows.stream().findFirst();
	}

	public BugReportResponse updateStatus(long bugReportId, String status) {
		int updated = jdbcTemplate.update("UPDATE bug_reports SET status = ? WHERE bug_report_id = ?", normalizeStatus(status), bugReportId);
		if (updated == 0) {
			throw new IllegalArgumentException("버그 리포트를 찾을 수 없습니다.");
		}
		return find(bugReportId).orElseThrow(() -> new IllegalArgumentException("버그 리포트를 찾을 수 없습니다."));
	}

	private BugReportResponse mapReport(ResultSet rs, int rowNum) throws SQLException {
		return new BugReportResponse(
				rs.getLong("bug_report_id"),
				nullableLong(rs, "reporter_user_id"),
				rs.getString("title"),
				rs.getString("description"),
				rs.getString("status"),
				rs.getString("severity"),
				rs.getString("app_version"),
				rs.getString("device_model"),
				toLocalDateTime(rs, "created_at"),
				toLocalDateTime(rs, "updated_at")
		);
	}

	private String normalizeStatus(String status) {
		if (status == null || status.isBlank()) {
			return "OPEN";
		}
		String normalized = status.trim().toUpperCase();
		if (!List.of("OPEN", "TRIAGED", "IN_PROGRESS", "RESOLVED", "REJECTED").contains(normalized)) {
			throw new IllegalArgumentException("status 값이 올바르지 않습니다.");
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
			sql.append(" AND (created_at < ? OR (created_at = ? AND bug_report_id < ?))");
			args.add(cursor.createdAt());
			args.add(cursor.createdAt());
			args.add(cursor.id());
		}
	}
}
