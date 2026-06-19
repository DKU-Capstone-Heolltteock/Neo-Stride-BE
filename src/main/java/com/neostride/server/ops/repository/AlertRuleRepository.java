package com.neostride.server.ops.repository;

import com.neostride.server.ops.dto.AlertRuleRequest;
import com.neostride.server.ops.dto.AlertRuleResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AlertRuleRepository {
	private final JdbcTemplate jdbcTemplate;

	public AlertRuleRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<AlertRuleResponse> list() {
		return jdbcTemplate.query("""
				SELECT alert_rule_id, name, metric_type, threshold_value, window_minutes, channel, enabled,
				       discord_status, discord_error, last_tested_at, created_by_operator_account_id, created_at, updated_at
				FROM operator_alert_rules
				ORDER BY created_at DESC, alert_rule_id DESC
				""", this::mapRule);
	}

	public Optional<AlertRuleResponse> find(long ruleId) {
		List<AlertRuleResponse> rows = jdbcTemplate.query("""
				SELECT alert_rule_id, name, metric_type, threshold_value, window_minutes, channel, enabled,
				       discord_status, discord_error, last_tested_at, created_by_operator_account_id, created_at, updated_at
				FROM operator_alert_rules
				WHERE alert_rule_id = ?
				""", this::mapRule, ruleId);
		return rows.stream().findFirst();
	}

	public AlertRuleResponse create(AlertRuleRequest request, long operatorAccountId) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var ps = connection.prepareStatement("""
					INSERT INTO operator_alert_rules (
					    name, metric_type, threshold_value, window_minutes, enabled, created_by_operator_account_id
					)
					VALUES (?, ?, ?, ?, ?, ?)
					""", java.sql.Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, request.name().trim());
			ps.setString(2, normalizeMetricType(request.metricType()));
			ps.setDouble(3, request.thresholdValue());
			ps.setInt(4, request.windowMinutes());
			ps.setBoolean(5, request.enabled() == null || request.enabled());
			ps.setLong(6, operatorAccountId);
			return ps;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("알림 정책 ID를 생성하지 못했습니다.");
		}
		return find(key.longValue()).orElseThrow(() -> new IllegalStateException("알림 정책을 저장하지 못했습니다."));
	}

	public AlertRuleResponse updateTestResult(long ruleId, String discordStatus, String discordError) {
		int updated = jdbcTemplate.update("""
				UPDATE operator_alert_rules
				SET discord_status = ?,
				    discord_error = ?,
				    last_tested_at = NOW()
				WHERE alert_rule_id = ?
				""", discordStatus, discordError, ruleId);
		if (updated == 0) {
			throw new IllegalArgumentException("알림 정책을 찾을 수 없습니다.");
		}
		return find(ruleId).orElseThrow(() -> new IllegalArgumentException("알림 정책을 찾을 수 없습니다."));
	}

	private AlertRuleResponse mapRule(ResultSet rs, int rowNum) throws SQLException {
		return new AlertRuleResponse(
				rs.getLong("alert_rule_id"),
				rs.getString("name"),
				rs.getString("metric_type"),
				rs.getDouble("threshold_value"),
				rs.getInt("window_minutes"),
				rs.getString("channel"),
				rs.getBoolean("enabled"),
				rs.getString("discord_status"),
				rs.getString("discord_error"),
				toLocalDateTime(rs, "last_tested_at"),
				nullableLong(rs, "created_by_operator_account_id"),
				toLocalDateTime(rs, "created_at"),
				toLocalDateTime(rs, "updated_at")
		);
	}

	private String normalizeMetricType(String metricType) {
		if (metricType == null || metricType.isBlank()) {
			throw new IllegalArgumentException("metric_type은 필수입니다.");
		}
		String normalized = metricType.trim().toUpperCase();
		if (!List.of("API_ERROR_RATE", "API_TRAFFIC", "SERVER_ERROR_COUNT").contains(normalized)) {
			throw new IllegalArgumentException("metric_type 값이 올바르지 않습니다.");
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
}
