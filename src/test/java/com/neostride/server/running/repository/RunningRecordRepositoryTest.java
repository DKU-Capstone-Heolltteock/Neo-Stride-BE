package com.neostride.server.running.repository;

import com.neostride.server.running.dto.GpsTraceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunningRecordRepositoryTest {

	@Test
	void insertRunningRecordPersistsDurationAndPaceAsIntegers() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		Connection connection = mock(Connection.class);
		PreparedStatement ps = mock(PreparedStatement.class);
		when(connection.prepareStatement(anyString(), eq(java.sql.Statement.RETURN_GENERATED_KEYS))).thenReturn(ps);
		doAnswer(invocation -> {
			var creator = invocation.getArgument(0, org.springframework.jdbc.core.PreparedStatementCreator.class);
			var keyHolder = invocation.getArgument(1, org.springframework.jdbc.support.KeyHolder.class);
			creator.createPreparedStatement(connection);
			keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 10L));
			return 1;
		}).when(jdbcTemplate).update(any(org.springframework.jdbc.core.PreparedStatementCreator.class), any(org.springframework.jdbc.support.KeyHolder.class));
		RunningRecordRepository repository = new RunningRecordRepository(jdbcTemplate);

		repository.insertRunningRecord(new com.neostride.server.running.dto.RunningRecordRequest(
				7L,
				null,
				new BigDecimal("5.23"),
				1800,
				346,
				new BigDecimal("310.56"),
				"",
				List.of(new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", null, null))
		));

		verify(ps).setInt(4, 1800);
		verify(ps).setInt(5, 346);
	}

	@Test
	void insertRunningRecordPersistsSecondPaceAsInteger() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		Connection connection = mock(Connection.class);
		PreparedStatement ps = mock(PreparedStatement.class);
		when(connection.prepareStatement(anyString(), eq(java.sql.Statement.RETURN_GENERATED_KEYS))).thenReturn(ps);
		doAnswer(invocation -> {
			var creator = invocation.getArgument(0, org.springframework.jdbc.core.PreparedStatementCreator.class);
			var keyHolder = invocation.getArgument(1, org.springframework.jdbc.support.KeyHolder.class);
			creator.createPreparedStatement(connection);
			keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 11L));
			return 1;
		}).when(jdbcTemplate).update(any(org.springframework.jdbc.core.PreparedStatementCreator.class), any(org.springframework.jdbc.support.KeyHolder.class));
		RunningRecordRepository repository = new RunningRecordRepository(jdbcTemplate);

		repository.insertRunningRecord(new com.neostride.server.running.dto.RunningRecordRequest(
				7L,
				null,
				new BigDecimal("5.23"),
				1800,
				392,
				new BigDecimal("310.56"),
				"",
				List.of(new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", null, null))
		));

		verify(ps).setInt(5, 392);
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void findByUserIdSelectsPlanIdForCoachingRecords() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of());
		RunningRecordRepository repository = new RunningRecordRepository(jdbcTemplate);

		repository.findByUserId(7L);

		var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(7L));
		assertThat(sqlCaptor.getValue()).contains("plan_id");
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void insertGpsTracesPersistsNullableHeartRateAndCadenceColumns() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(2);
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
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(2);
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

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void insertGpsTracesUsesLegacyColumnsWhenWatchMetricColumnsAreMissing() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
		RunningRecordRepository repository = new RunningRecordRepository(jdbcTemplate);
		GpsTraceRequest trace = new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", 150.0, 171.0);

		repository.insertGpsTraces(10L, List.of(trace));

		var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		var setterCaptor = org.mockito.ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
		verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), anyList(), anyInt(), setterCaptor.capture());
		assertThat(sqlCaptor.getValue()).doesNotContain("heart_rate", "cadence");

		PreparedStatement ps = mock(PreparedStatement.class);
		((ParameterizedPreparedStatementSetter<GpsTraceRequest>) setterCaptor.getValue()).setValues(ps, trace);
		verify(ps).setLong(1, 10L);
		verify(ps).setBigDecimal(2, new BigDecimal("126.9780000"));
		verify(ps).setBigDecimal(3, new BigDecimal("37.5665000"));
		verify(ps).setObject(4, LocalDateTime.of(2026, 5, 12, 18, 0));
		verify(ps, never()).setObject(eq(5), any());
		verify(ps, never()).setObject(eq(6), any());
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void findGpsTracesUsesLegacySelectAndReturnsNullWatchMetricsWhenColumnsAreMissing() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10L))).thenReturn(List.of());
		RunningRecordRepository repository = new RunningRecordRepository(jdbcTemplate);

		Method method = RunningRecordRepository.class.getDeclaredMethod("findGpsTracesByRecordId", long.class);
		method.setAccessible(true);
		List<GpsTraceRequest> traces = (List<GpsTraceRequest>) method.invoke(repository, 10L);

		var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(10L));
		assertThat(sqlCaptor.getValue()).contains("latitude", "longitude", "recorded_time");
		assertThat(sqlCaptor.getValue()).doesNotContain("heart_rate", "cadence");
		assertThat(traces).isEmpty();
	}
}
