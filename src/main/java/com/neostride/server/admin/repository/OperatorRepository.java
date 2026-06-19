package com.neostride.server.admin.repository;

import com.neostride.server.admin.security.OperatorPermissions;
import com.neostride.server.admin.security.OperatorPrincipal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
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
				""", (rs, rowNum) -> new OperatorAccountRow(
				rs.getLong("operator_account_id"),
				rs.getString("email"),
				rs.getString("password"),
				rs.getString("name"),
				rs.getString("role"),
				rs.getString("status")
		), email);
		return rows.stream().findFirst();
	}

	public Optional<OperatorPrincipal> findPrincipal(long operatorAccountId) {
		List<OperatorAccountRow> rows = jdbcTemplate.query("""
				SELECT operator_account_id, email, password, name, role, status
				FROM operator_accounts
				WHERE operator_account_id = ?
				""", (rs, rowNum) -> new OperatorAccountRow(
				rs.getLong("operator_account_id"),
				rs.getString("email"),
				rs.getString("password"),
				rs.getString("name"),
				rs.getString("role"),
				rs.getString("status")
		), operatorAccountId);
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

	private List<String> findExplicitPermissions(long operatorAccountId) {
		return jdbcTemplate.query("""
				SELECT permission
				FROM operator_account_permissions
				WHERE operator_account_id = ?
				ORDER BY permission
				""", (rs, rowNum) -> rs.getString("permission"), operatorAccountId);
	}
}
