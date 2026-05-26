package com.neostride.server.running.repository;

import com.neostride.server.running.dto.GpsTraceRequest;
import com.neostride.server.running.dto.RunningRecordRequest;
import com.neostride.server.running.dto.RunningRecordResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RunningRecordRepository {

	private static final DateTimeFormatter TRACE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter RESPONSE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final JdbcTemplate jdbcTemplate;
	private volatile Boolean watchMetricColumnsAvailable;

	private final RowMapper<RunningRecordResponse> runningRecordRowMapper = (rs, rowNum) -> RunningRecordResponse.record(
			rs.getLong("run_record_id"),
			nullableLong(rs.getObject("plan_id")),
			rs.getTimestamp("created_at").toLocalDateTime().format(RESPONSE_TIME_FORMATTER),
			rs.getBigDecimal("total_distance"),
			nullableInt(rs.getObject("duration")),
			rs.getBigDecimal("pace"),
			nullableInt(rs.getObject("calories")),
			findGpsTracesByRecordId(rs.getLong("run_record_id")),
			List.of()
	);

	public RunningRecordRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public long insertRunningRecord(RunningRecordRequest request) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO running_records
						(user_id, plan_id, total_distance, duration, pace, calories, route_detail)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, request.userId());
			if (request.planId() == null) {
				ps.setObject(2, null);
			} else {
				ps.setLong(2, request.planId());
			}
			ps.setBigDecimal(3, request.totalDistance().setScale(2, RoundingMode.HALF_UP));
			ps.setInt(4, roundedInt(request.duration()));
			ps.setBigDecimal(5, request.pace().setScale(2, RoundingMode.HALF_UP));
			ps.setInt(6, roundedInt(request.calories()));
			ps.setString(7, request.routeDetail());
			return ps;
		}, keyHolder);

		Number generatedId = keyHolder.getKey();
		if (generatedId == null) {
			throw new IllegalStateException("러닝 기록 ID를 생성하지 못했습니다.");
		}
		return generatedId.longValue();
	}

	public void insertGpsTraces(long runRecordId, List<GpsTraceRequest> gpsTraces) {
		if (supportsWatchMetricColumns()) {
			jdbcTemplate.batchUpdate("""
					INSERT INTO gps_traces
						(run_record_id, longitude, latitude, recorded_time, heart_rate, cadence)
					VALUES (?, ?, ?, ?, ?, ?)
					""", gpsTraces, gpsTraces.size(), (ps, trace) -> {
				ps.setLong(1, runRecordId);
				ps.setBigDecimal(2, BigDecimal.valueOf(trace.longitude()).setScale(7, RoundingMode.HALF_UP));
				ps.setBigDecimal(3, BigDecimal.valueOf(trace.latitude()).setScale(7, RoundingMode.HALF_UP));
				ps.setObject(4, parseTraceTime(trace.time()));
				ps.setObject(5, nullableDecimal(trace.heartRate()));
				ps.setObject(6, nullableDecimal(trace.cadence()));
			});
			return;
		}

		jdbcTemplate.batchUpdate("""
				INSERT INTO gps_traces
					(run_record_id, longitude, latitude, recorded_time)
				VALUES (?, ?, ?, ?)
				""", gpsTraces, gpsTraces.size(), (ps, trace) -> {
			ps.setLong(1, runRecordId);
			ps.setBigDecimal(2, BigDecimal.valueOf(trace.longitude()).setScale(7, RoundingMode.HALF_UP));
			ps.setBigDecimal(3, BigDecimal.valueOf(trace.latitude()).setScale(7, RoundingMode.HALF_UP));
			ps.setObject(4, parseTraceTime(trace.time()));
		});
	}

	public List<RunningRecordResponse> findByUserId(long userId) {
		return jdbcTemplate.query("""
				SELECT run_record_id, plan_id, created_at, total_distance, duration, pace, calories
				FROM running_records
				WHERE user_id = ?
				ORDER BY created_at DESC, run_record_id DESC
				""", runningRecordRowMapper, userId);
	}

	public List<RunningRecordResponse> findByUserIdAndMonth(long userId, int year, int month) {
		return jdbcTemplate.query("""
				SELECT run_record_id, plan_id, created_at, total_distance, duration, pace, calories
				FROM running_records
				WHERE user_id = ? AND YEAR(created_at) = ? AND MONTH(created_at) = ?
				ORDER BY created_at DESC, run_record_id DESC
				""", runningRecordRowMapper, userId, year, month);
	}

	public RunningRecordResponse findByRecordIdForUser(long userId, long recordId) {
		List<RunningRecordResponse> records = jdbcTemplate.query("""
				SELECT run_record_id, plan_id, created_at, total_distance, duration, pace, calories
				FROM running_records
				WHERE user_id = ? AND run_record_id = ?
				""", runningRecordRowMapper, userId, recordId);
		return records.isEmpty() ? null : records.getFirst();
	}

	private List<GpsTraceRequest> findGpsTracesByRecordId(long recordId) {
		if (supportsWatchMetricColumns()) {
			return jdbcTemplate.query("""
					SELECT latitude, longitude, recorded_time, heart_rate, cadence
					FROM gps_traces
					WHERE run_record_id = ?
					ORDER BY recorded_time ASC, trace_id ASC
					""", (rs, rowNum) -> new GpsTraceRequest(
					rs.getDouble("latitude"),
					rs.getDouble("longitude"),
					rs.getTimestamp("recorded_time").toLocalDateTime().format(TRACE_TIME_FORMATTER),
					nullableDouble(rs.getObject("heart_rate")),
					nullableDouble(rs.getObject("cadence"))
			), recordId);
		}

		return jdbcTemplate.query("""
				SELECT latitude, longitude, recorded_time
				FROM gps_traces
				WHERE run_record_id = ?
				ORDER BY recorded_time ASC, trace_id ASC
				""", (rs, rowNum) -> new GpsTraceRequest(
				rs.getDouble("latitude"),
				rs.getDouble("longitude"),
				rs.getTimestamp("recorded_time").toLocalDateTime().format(TRACE_TIME_FORMATTER),
				null,
				null
		), recordId);
	}

	private LocalDateTime parseTraceTime(String time) {
		return LocalDateTime.parse(time, TRACE_TIME_FORMATTER);
	}

	private boolean supportsWatchMetricColumns() {
		Boolean cached = watchMetricColumnsAvailable;
		if (cached != null) {
			return cached;
		}

		Integer columnCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = DATABASE()
					AND table_name = 'gps_traces'
					AND column_name IN ('heart_rate', 'cadence')
				""", Integer.class);
		boolean available = columnCount != null && columnCount == 2;
		watchMetricColumnsAvailable = available;
		return available;
	}

	private int roundedInt(BigDecimal value) {
		return value.setScale(0, RoundingMode.HALF_UP).intValueExact();
	}

	private static BigDecimal nullableDecimal(Double value) {
		return value == null ? null : BigDecimal.valueOf(value);
	}

	private static Double nullableDouble(Object value) {
		return value == null ? null : ((Number) value).doubleValue();
	}

	private static Integer nullableInt(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}

	private static Long nullableLong(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}
}
