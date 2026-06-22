package com.neostride.server.devtools.repository;

import com.neostride.server.devtools.api.ErrorEventRecorder;
import com.neostride.server.devtools.dto.ErrorEventResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ErrorEventRepository implements ErrorEventRecorder {
	private final JdbcTemplate jdbcTemplate;

	public ErrorEventRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void record(String method, String path, int statusCode, String errorType, String messageSummary, String requestId) {
		try {
			jdbcTemplate.update("""
					INSERT INTO server_error_events (method, path, status_code, error_type, message_summary, request_id)
					VALUES (?, ?, ?, ?, ?, ?)
					""", method, truncate(path, 255), statusCode, truncate(errorType, 120), sanitize(messageSummary), truncate(requestId, 120));
		} catch (RuntimeException ignored) {
		}
	}

	public List<ErrorEventResponse> recent(String query, Integer statusCode, int limit) {
		List<Object> args = new ArrayList<>();
		StringBuilder sql = new StringBuilder("""
				SELECT server_error_event_id, method, path, status_code, error_type, message_summary, request_id, created_at
				FROM server_error_events
				WHERE 1 = 1
				""");
		if (query != null && !query.isBlank()) {
			sql.append(" AND (path LIKE ? OR error_type LIKE ? OR message_summary LIKE ?)");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (statusCode != null) {
			sql.append(" AND status_code = ?");
			args.add(statusCode);
		}
		sql.append(" ORDER BY created_at DESC, server_error_event_id DESC LIMIT ?");
		args.add(Math.min(Math.max(limit, 1), 200));
		return jdbcTemplate.query(sql.toString(), this::mapEvent, args.toArray());
	}

	private ErrorEventResponse mapEvent(ResultSet rs, int rowNum) throws SQLException {
		return new ErrorEventResponse(
				rs.getLong("server_error_event_id"),
				rs.getString("method"),
				rs.getString("path"),
				rs.getInt("status_code"),
				rs.getString("error_type"),
				rs.getString("message_summary"),
				rs.getString("request_id"),
				toLocalDateTime(rs, "created_at")
		);
	}

	private String sanitize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String lower = value.toLowerCase();
		if (lower.contains("token") || lower.contains("password") || lower.contains("authorization") || lower.contains("gps")) {
			return "redacted";
		}
		return truncate(value, 500);
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private LocalDateTime toLocalDateTime(ResultSet rs, String columnName) throws SQLException {
		var timestamp = rs.getTimestamp(columnName);
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}
}
