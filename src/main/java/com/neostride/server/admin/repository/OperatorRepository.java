package com.neostride.server.admin.repository;

import com.neostride.server.admin.dto.OperatorAccountResponse;
import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.platform.web.CursorSupport;
import com.neostride.server.platform.web.CursorSupport.CursorPosition;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OperatorRepository {
	private final JdbcTemplate jdbcTemplate;

	public OperatorRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<OperatorAccountRow> findByEmail(String email) {
		List<OperatorAccountRow> rows = jdbcTemplate.query("""
				SELECT operator_account_id, email, password, name, role, status
				FROM operator_accounts
				WHERE email = ?
				""", this::mapAccountRow, email);
		return rows.stream().findFirst();
	}

	public Optional<OperatorPrincipal> findPrincipal(long operatorAccountId) {
		List<OperatorAccountRow> rows = jdbcTemplate.query("""
				SELECT operator_account_id, email, password, name, role, status
				FROM operator_accounts
				WHERE operator_account_id = ?
				""", this::mapAccountRow, operatorAccountId);
		return rows.stream()
				.filter(row -> "ACTIVE".equals(row.status()))
				.map(this::toPrincipal)
				.findFirst();
	}

	public OperatorPrincipal toPrincipal(OperatorAccountRow account) {
		Set<String> permissions = new LinkedHashSet<>(OperatorPermissions.defaultsForRole(account.role()));
		permissions.addAll(findExplicitPermissions(account.operatorAccountId()));
		return new OperatorPrincipal(
				account.operatorAccountId(),
				account.email(),
				account.name(),
				account.role(),
				List.copyOf(permissions)
		);
	}

	public void markLogin(long operatorAccountId) {
		jdbcTemplate.update("UPDATE operator_accounts SET last_login_at = NOW() WHERE operator_account_id = ?", operatorAccountId);
	}

	public List<OperatorAccountResponse> listAccounts(String role, String status, int limit) {
		return listAccounts(role, status, null, null, null, limit);
	}

	public List<OperatorAccountResponse> listAccounts(String role, String status, CursorPosition cursor, LocalDateTime from, LocalDateTime to, int limit) {
		List<Object> args = new ArrayList<>();
		StringBuilder sql = new StringBuilder("""
				SELECT operator_account_id, email, name, role, status, last_login_at, created_at, updated_at
				FROM operator_accounts
				WHERE 1 = 1
				""");
		if (role != null && !role.isBlank()) {
			sql.append(" AND role = ?");
			args.add(role.trim());
		}
		if (status != null && !status.isBlank()) {
			sql.append(" AND status = ?");
			args.add(status.trim());
		}
		appendRangeAndCursor(sql, args, "operator_account_id", cursor, from, to);
		sql.append(" ORDER BY created_at DESC, operator_account_id DESC LIMIT ?");
		args.add(CursorSupport.cappedFetchLimit(limit));
		return jdbcTemplate.query(sql.toString(), this::mapAccountResponse, args.toArray());
	}

	public Optional<OperatorAccountResponse> findAccount(long operatorAccountId) {
		List<OperatorAccountResponse> rows = jdbcTemplate.query("""
				SELECT operator_account_id, email, name, role, status, last_login_at, created_at, updated_at
				FROM operator_accounts
				WHERE operator_account_id = ?
				""", this::mapAccountResponse, operatorAccountId);
		return rows.stream().findFirst();
	}

	public OperatorAccountResponse createAccount(String email, String passwordHash, String name, String role, String status, List<String> explicitPermissions) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var preparedStatement = connection.prepareStatement("""
					INSERT INTO operator_accounts (email, password, name, role, status)
					VALUES (?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			preparedStatement.setString(1, email);
			preparedStatement.setString(2, passwordHash);
			preparedStatement.setString(3, name);
			preparedStatement.setString(4, role);
			preparedStatement.setString(5, status);
			return preparedStatement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("운영자 계정을 생성할 수 없습니다.");
		}
		long operatorAccountId = key.longValue();
		replaceExplicitPermissions(operatorAccountId, explicitPermissions);
		return findAccount(operatorAccountId)
				.orElseThrow(() -> new IllegalStateException("생성된 운영자 계정을 조회할 수 없습니다."));
	}

	public OperatorAccountResponse updateStatus(long operatorAccountId, String status) {
		int updated = jdbcTemplate.update("UPDATE operator_accounts SET status = ? WHERE operator_account_id = ?", status, operatorAccountId);
		if (updated == 0) {
			throw new IllegalArgumentException("운영자 계정을 찾을 수 없습니다.");
		}
		return findAccount(operatorAccountId)
				.orElseThrow(() -> new IllegalArgumentException("운영자 계정을 찾을 수 없습니다."));
	}

	public OperatorAccountResponse updateAccount(long operatorAccountId, String email, String name, String role) {
		int updated = jdbcTemplate.update("""
				UPDATE operator_accounts
				SET email = ?,
				    name = ?,
				    role = ?
				WHERE operator_account_id = ?
				""", email, name, role, operatorAccountId);
		if (updated == 0) {
			throw new IllegalArgumentException("운영자 계정을 찾을 수 없습니다.");
		}
		return findAccount(operatorAccountId)
				.orElseThrow(() -> new IllegalArgumentException("운영자 계정을 찾을 수 없습니다."));
	}

	public OperatorAccountResponse replacePermissions(long operatorAccountId, List<String> explicitPermissions) {
		if (findAccount(operatorAccountId).isEmpty()) {
			throw new IllegalArgumentException("운영자 계정을 찾을 수 없습니다.");
		}
		replaceExplicitPermissions(operatorAccountId, explicitPermissions);
		return findAccount(operatorAccountId)
				.orElseThrow(() -> new IllegalArgumentException("운영자 계정을 찾을 수 없습니다."));
	}

	private void replaceExplicitPermissions(long operatorAccountId, List<String> explicitPermissions) {
		jdbcTemplate.update("DELETE FROM operator_account_permissions WHERE operator_account_id = ?", operatorAccountId);
		List<String> safePermissions = explicitPermissions == null ? List.of() : explicitPermissions;
		for (String permission : safePermissions) {
			jdbcTemplate.update("""
					INSERT INTO operator_account_permissions (operator_account_id, permission)
					VALUES (?, ?)
					""", operatorAccountId, permission);
		}
	}

	private OperatorAccountRow mapAccountRow(ResultSet rs, int rowNum) throws SQLException {
		return new OperatorAccountRow(
				rs.getLong("operator_account_id"),
				rs.getString("email"),
				rs.getString("password"),
				rs.getString("name"),
				rs.getString("role"),
				rs.getString("status")
		);
	}

	private OperatorAccountResponse mapAccountResponse(ResultSet rs, int rowNum) throws SQLException {
		long operatorAccountId = rs.getLong("operator_account_id");
		String role = rs.getString("role");
		List<String> rolePermissions = OperatorPermissions.defaultsForRole(role);
		List<String> explicitPermissions = findExplicitPermissions(operatorAccountId);
		Set<String> permissions = new LinkedHashSet<>(rolePermissions);
		permissions.addAll(explicitPermissions);
		return new OperatorAccountResponse(
				operatorAccountId,
				rs.getString("email"),
				rs.getString("name"),
				role,
				rs.getString("status"),
				rolePermissions,
				explicitPermissions,
				List.copyOf(permissions),
				toLocalDateTime(rs, "last_login_at"),
				toLocalDateTime(rs, "created_at"),
				toLocalDateTime(rs, "updated_at")
		);
	}

	private List<String> findExplicitPermissions(long operatorAccountId) {
		return jdbcTemplate.query("""
				SELECT permission
				FROM operator_account_permissions
				WHERE operator_account_id = ?
				ORDER BY permission
				""", (rs, rowNum) -> rs.getString("permission"), operatorAccountId);
	}

	private LocalDateTime toLocalDateTime(ResultSet rs, String columnName) throws SQLException {
		var timestamp = rs.getTimestamp(columnName);
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}

	private void appendRangeAndCursor(StringBuilder sql, List<Object> args, String idColumn, CursorPosition cursor, LocalDateTime from, LocalDateTime to) {
		if (from != null) {
			sql.append(" AND created_at >= ?");
			args.add(from);
		}
		if (to != null) {
			sql.append(" AND created_at <= ?");
			args.add(to);
		}
		if (cursor != null) {
			sql.append(" AND (created_at < ? OR (created_at = ? AND ").append(idColumn).append(" < ?))");
			args.add(cursor.createdAt());
			args.add(cursor.createdAt());
			args.add(cursor.id());
		}
	}
}
