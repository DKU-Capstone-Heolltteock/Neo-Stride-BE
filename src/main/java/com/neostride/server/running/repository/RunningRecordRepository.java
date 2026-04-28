package com.neostride.server.running.repository;

import com.neostride.server.running.dto.GpsTraceRequest;
import com.neostride.server.running.dto.RunningRecordRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RunningRecordRepository {

	private static final DateTimeFormatter TRACE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final JdbcTemplate jdbcTemplate;

	public RunningRecordRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public long insertRunningRecord(RunningRecordRequest request) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO running_records
						(user_id, plan_id, total_distance, duration, pace, calories)
					VALUES (?, ?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, request.userId());
			if (request.planId() == null) {
				ps.setObject(2, null);
			} else {
				ps.setLong(2, request.planId());
			}
			ps.setBigDecimal(3, request.totalDistance().setScale(2, RoundingMode.HALF_UP));
			ps.setInt(4, roundedInt(request.duration()));
			ps.setInt(5, roundedInt(request.pace()));
			ps.setInt(6, roundedInt(request.calories()));
			return ps;
		}, keyHolder);

		Number generatedId = keyHolder.getKey();
		if (generatedId == null) {
			throw new IllegalStateException("러닝 기록 ID를 생성하지 못했습니다.");
		}
		return generatedId.longValue();
	}

	public void insertGpsTraces(long runRecordId, List<GpsTraceRequest> gpsTraces) {
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

	private LocalDateTime parseTraceTime(String time) {
		return LocalDateTime.parse(time, TRACE_TIME_FORMATTER);
	}

	private int roundedInt(BigDecimal value) {
		return value.setScale(0, RoundingMode.HALF_UP).intValueExact();
	}
}
