package com.neostride.server.running.repository;

import com.neostride.server.running.dto.GpsTraceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RunningRecordRepositoryTest {

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void insertGpsTracesPersistsNullableHeartRateAndCadenceColumns() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		RunningRecordRepository repository = new RunningRecordRepository(jdbcTemplate);
		GpsTraceRequest trace = new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", 150.0, 171.0);

		repository.insertGpsTraces(10L, List.of(trace));

		var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		var setterCaptor = org.mockito.ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
		verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), anyList(), anyInt(), setterCaptor.capture());
		assertThat(sqlCaptor.getValue()).contains("heart_rate", "cadence");

		PreparedStatement ps = mock(PreparedStatement.class);
		((ParameterizedPreparedStatementSetter<GpsTraceRequest>) setterCaptor.getValue()).setValues(ps, trace);
		verify(ps).setLong(1, 10L);
		verify(ps).setBigDecimal(2, new BigDecimal("126.9780000"));
		verify(ps).setBigDecimal(3, new BigDecimal("37.5665000"));
		verify(ps).setObject(4, LocalDateTime.of(2026, 5, 12, 18, 0));
		verify(ps).setObject(5, new BigDecimal("150.0"));
		verify(ps).setObject(6, new BigDecimal("171.0"));
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void insertGpsTracesPersistsNullForLegacyHeartRateAndCadence() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		RunningRecordRepository repository = new RunningRecordRepository(jdbcTemplate);
		GpsTraceRequest trace = new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", null, null);

		repository.insertGpsTraces(10L, List.of(trace));

		var setterCaptor = org.mockito.ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
		verify(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), setterCaptor.capture());

		PreparedStatement ps = mock(PreparedStatement.class);
		((ParameterizedPreparedStatementSetter<GpsTraceRequest>) setterCaptor.getValue()).setValues(ps, trace);
		verify(ps).setObject(5, null);
		verify(ps).setObject(6, null);
	}
}
