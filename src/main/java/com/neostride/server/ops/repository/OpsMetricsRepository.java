package com.neostride.server.ops.repository;

import com.neostride.server.ops.dto.ApiTrafficMetricResponse;
import com.neostride.server.ops.dto.ErrorMetricResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OpsMetricsRepository {
	private final JdbcTemplate jdbcTemplate;

	public OpsMetricsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void record(String method, String path, int statusCode, long durationMs) {
		try {
			jdbcTemplate.update("""
					INSERT INTO api_request_metrics (method, path, status_code, duration_ms)
					VALUES (?, ?, ?, ?)
					""", method, truncate(path, 255), statusCode, durationMs);
		} catch (RuntimeException ignored) {
		}
	}

	public List<ApiTrafficMetricResponse> apiTraffic(int hours, int limit) {
		return jdbcTemplate.query("""
				SELECT method,
				       path,
				       COUNT(*) AS request_count,
				       SUM(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END) AS error_count,
				       AVG(duration_ms) AS average_duration_ms,
				       MAX(duration_ms) AS p95_duration_ms
				FROM api_request_metrics
				WHERE occurred_at >= TIMESTAMPADD(HOUR, -?, NOW())
				GROUP BY method, path
				ORDER BY request_count DESC, path
				LIMIT ?
				""", (rs, rowNum) -> new ApiTrafficMetricResponse(
				rs.getString("method"),
				rs.getString("path"),
				rs.getLong("request_count"),
				rs.getLong("error_count"),
				rs.getDouble("average_duration_ms"),
				rs.getLong("p95_duration_ms")
		), Math.max(1, hours), Math.min(Math.max(limit, 1), 200));
	}

	public List<ErrorMetricResponse> errors(int hours) {
		return jdbcTemplate.query("""
				SELECT status_code, COUNT(*) AS error_count
				FROM api_request_metrics
				WHERE occurred_at >= TIMESTAMPADD(HOUR, -?, NOW())
				  AND status_code >= 400
				GROUP BY status_code
				ORDER BY status_code
				""", (rs, rowNum) -> new ErrorMetricResponse(rs.getInt("status_code"), rs.getLong("error_count")), Math.max(1, hours));
	}

	public long countRequestsLast24h() {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM api_request_metrics
				WHERE occurred_at >= TIMESTAMPADD(HOUR, -24, NOW())
				""", Long.class);
		return count == null ? 0 : count;
	}

	public long countErrorsLast24h() {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM api_request_metrics
				WHERE occurred_at >= TIMESTAMPADD(HOUR, -24, NOW())
				  AND status_code >= 500
				""", Long.class);
		return count == null ? 0 : count;
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}
}
