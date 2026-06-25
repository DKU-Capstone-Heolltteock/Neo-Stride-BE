package com.neostride.server.crew.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CrewRepositoryTest {
	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final CrewRepository repository = new CrewRepository(jdbcTemplate);

	@Test
	void listAcceptedMembersExcludesSoftDeletedUsers() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(List.of());

		repository.listAcceptedMembers(1L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(1L));
		assertThat(sql.getValue()).contains("JOIN users u", "cm.status = 'ACCEPTED' AND u.deleted_at IS NULL");
	}

	@Test
	void listInstantParticipantRequestsExcludesSoftDeletedUsers() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10L))).thenReturn(List.of());

		repository.listInstantParticipantRequests(10L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(10L));
		assertThat(sql.getValue()).contains("p.status = 'REQUESTED' AND u.deleted_at IS NULL");
	}

	@Test
	void listCrewMessagesAnonymizesDeletedSendersWithoutDroppingHistory() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		repository.listCrewMessages(1L, null, 20);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertThat(sql.getValue()).contains("ELSE '탈퇴한 사용자' END AS nickname", "ELSE NULL END AS profile_image_url");
	}
}
