package com.neostride.server.coaching.repository;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoachingRepositoryTest {

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void findTodayPlanIncludesCompletedInactiveGoalForFeedbackReadback() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		LocalDate today = LocalDate.parse("2026-05-28");
		when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(1000004L), eq(today)))
				.thenReturn(List.of());
		CoachingRepository repository = new CoachingRepository(jdbcTemplate);

		repository.findTodayPlanByUserId(1000004L, today);

		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(1000004L), eq(today));
		assertThat(sqlCaptor.getValue())
				.contains("p.user_id = ? AND p.plan_date = ?")
				.contains("g.is_active = TRUE OR p.is_completed = TRUE OR p.feedback IS NOT NULL")
				.contains("ORDER BY g.is_active DESC, p.is_completed DESC, p.updated_at DESC, p.plan_id DESC");
	}
}
