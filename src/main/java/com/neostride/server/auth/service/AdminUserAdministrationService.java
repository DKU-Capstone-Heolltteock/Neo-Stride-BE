package com.neostride.server.auth.service;

import com.neostride.server.auth.api.AdminUserAccount;
import com.neostride.server.auth.api.UserAdministrationPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserAdministrationService implements UserAdministrationPort {
	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 200;

	private final JdbcTemplate jdbcTemplate;

	public AdminUserAdministrationService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<AdminUserAccount> searchAccounts(String query, String status, int limit) {
		int normalizedLimit = normalizeLimit(limit);
		List<Object> args = new ArrayList<>();
		StringBuilder sql = new StringBuilder("""
				SELECT user_id, email, name, community_profile_name, profile_photo, account_status,
				       suspended_at, suspended_until, suspended_reason, created_at, updated_at
				FROM users
				WHERE 1 = 1
				""");
		if (query != null && !query.isBlank()) {
			sql.append(" AND (email LIKE ? OR name LIKE ? OR community_profile_name LIKE ?)");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (status != null && !status.isBlank()) {
			String normalizedStatus = normalizeStatus(status);
			sql.append(" AND account_status = ?");
			args.add(normalizedStatus);
		}
		sql.append(" ORDER BY created_at DESC, user_id DESC LIMIT ?");
		args.add(normalizedLimit);
		return jdbcTemplate.query(sql.toString(), this::mapAccount, args.toArray());
	}

	@Override
	public Optional<AdminUserAccount> findAccount(long userId) {
		if (userId <= 0) {
			throw new IllegalArgumentException("user_id는 1 이상의 값이어야 합니다.");
		}
		List<AdminUserAccount> accounts = jdbcTemplate.query("""
				SELECT user_id, email, name, community_profile_name, profile_photo, account_status,
				       suspended_at, suspended_until, suspended_reason, created_at, updated_at
				FROM users
				WHERE user_id = ?
				""", this::mapAccount, userId);
		return accounts.stream().findFirst();
	}

	@Override
	@Transactional
	public AdminUserAccount suspendAccount(long userId, long operatorAccountId, String reason, LocalDateTime suspendedUntil) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("reason은 필수입니다.");
		}
		int updated = jdbcTemplate.update("""
				UPDATE users
				SET account_status = 'SUSPENDED',
				    suspended_at = NOW(),
				    suspended_until = ?,
				    suspended_reason = ?,
				    suspended_by_operator_id = ?
				WHERE user_id = ?
				""", suspendedUntil, reason.trim(), operatorAccountId, userId);
		if (updated == 0) {
			throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
		}
		return findAccount(userId).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	@Override
	@Transactional
	public AdminUserAccount restoreAccount(long userId) {
		int updated = jdbcTemplate.update("""
				UPDATE users
				SET account_status = 'ACTIVE',
				    suspended_at = NULL,
				    suspended_until = NULL,
				    suspended_reason = NULL,
				    suspended_by_operator_id = NULL
				WHERE user_id = ?
				""", userId);
		if (updated == 0) {
			throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
		}
		return findAccount(userId).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	@Override
	public List<Long> activeUserIds(int limit) {
		return jdbcTemplate.query("""
				SELECT user_id
				FROM users
				WHERE account_status = 'ACTIVE'
				ORDER BY user_id
				LIMIT ?
				""", (rs, rowNum) -> rs.getLong("user_id"), normalizeLimit(limit));
	}

	@Override
	public long countAccounts(String status) {
		if (status == null || status.isBlank()) {
			Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
			return count == null ? 0 : count;
		}
		Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE account_status = ?", Long.class, normalizeStatus(status));
		return count == null ? 0 : count;
	}

	private AdminUserAccount mapAccount(ResultSet rs, int rowNum) throws SQLException {
		return new AdminUserAccount(
				rs.getLong("user_id"),
				rs.getString("email"),
				rs.getString("name"),
				rs.getString("community_profile_name"),
				rs.getString("profile_photo"),
				rs.getString("account_status"),
				toLocalDateTime(rs, "suspended_at"),
				toLocalDateTime(rs, "suspended_until"),
				rs.getString("suspended_reason"),
				toLocalDateTime(rs, "created_at"),
				toLocalDateTime(rs, "updated_at")
		);
	}

	private LocalDateTime toLocalDateTime(ResultSet rs, String columnName) throws SQLException {
		var timestamp = rs.getTimestamp(columnName);
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}

	private int normalizeLimit(int limit) {
		if (limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

	private String normalizeStatus(String status) {
		String normalized = status.trim().toUpperCase();
		if (!"ACTIVE".equals(normalized) && !"SUSPENDED".equals(normalized)) {
			throw new IllegalArgumentException("status는 ACTIVE 또는 SUSPENDED만 가능합니다.");
		}
		return normalized;
	}
}
