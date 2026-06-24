package com.neostride.server.audit.repository;

import com.neostride.server.audit.dto.AuditLogResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogRepositoryTest {
	@Test
	void searchBuildsFilteredQueryWithCappedLimit() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
		AuditLogRepository repository = new AuditLogRepository(jdbcTemplate);

		repository.search("log.search", "server_error_event", "42", 7L, 500);

		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());

		assertThat(sqlCaptor.getValue())
				.contains("action = ?")
				.contains("target_type = ?")
				.contains("target_id = ?")
				.contains("actor_operator_account_id = ?")
				.contains("ORDER BY created_at DESC, operator_audit_log_id DESC LIMIT ?");
		assertThat(argsCaptor.getValue()).containsExactly("log.search", "server_error_event", "42", 7L, 200);
	}

	@Test
	void searchPreservesMaxFetchLimitForCursorPagination() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
		AuditLogRepository repository = new AuditLogRepository(jdbcTemplate);

		repository.search(null, null, null, null, null, null, null, 201);

		ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(anyString(), any(RowMapper.class), argsCaptor.capture());

		assertThat(argsCaptor.getValue()).containsExactly(201);
	}

	@Test
	void findQueriesByPrimaryKey() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10L))).thenReturn(List.<AuditLogResponse>of());
		AuditLogRepository repository = new AuditLogRepository(jdbcTemplate);

		repository.find(10L);

		verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(10L));
	}
}
