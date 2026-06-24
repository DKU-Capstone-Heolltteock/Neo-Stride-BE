package com.neostride.server.running.repository;

import com.neostride.server.running.api.RunningAggregate;
import com.neostride.server.running.dto.GpsTraceRequest;
import com.neostride.server.running.dto.RunningRecordRequest;
import com.neostride.server.running.dto.RunningRecordResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
			rs.getBoolean("is_feed_linked"),
			nullableLong(rs.getObject("plan_id")),
			rs.getTimestamp("created_at").toLocalDateTime().format(RESPONSE_TIME_FORMATTER),
			rs.getBigDecimal("total_distance"),
			nullableInt(rs.getObject("duration")),
			nullableInt(rs.getObject("pace")),
			nullableInt(rs.getObject("calories")),
			List.of(),
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
						(user_id, plan_id, total_distance, duration, pace, calories, route_detail, badge)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, request.userId());
			if (request.planId() == null) {
				ps.setObject(2, null);
			} else {
				ps.setLong(2, request.planId());
			}
			ps.setBigDecimal(3, request.totalDistance().setScale(2, RoundingMode.HALF_UP));
			ps.setInt(4, request.duration());
			ps.setInt(5, request.pace());
			ps.setInt(6, roundedInt(request.calories()));
			ps.setString(7, request.routeDetail());
			ps.setString(8, normalizeBadge(request.badge()));
			return ps;
		}, keyHolder);

		Number generatedId = keyHolder.getKey();
		if (generatedId == null) {
			throw new IllegalStateException("러닝 기록 ID를 생성하지 못했습니다.");
		}
		return generatedId.longValue();
	}

	private static String normalizeBadge(String badge) {
		if (badge == null || badge.isBlank()) {
			return "NONE";
		}
		String normalized = badge.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER", "CHALLENGER" -> normalized;
			default -> "NONE";
		};
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
		List<RunningRecordResponse> records = jdbcTemplate.query("""
				SELECT rr.run_record_id, rr.plan_id, rr.created_at, rr.total_distance, rr.duration, rr.pace, rr.calories,
				       EXISTS (
				           SELECT 1
				           FROM community_contents cc
				           WHERE cc.running_record_id = rr.run_record_id
				             AND cc.content_type = 'POST'
				       ) AS is_feed_linked
				FROM running_records rr
				WHERE rr.user_id = ?
				ORDER BY rr.created_at DESC, rr.run_record_id DESC
				""", runningRecordRowMapper, userId);
		return attachGpsTraces(records);
	}

	public List<RunningRecordResponse> findByUserIdAndMonth(long userId, int year, int month) {
		LocalDate startDate = LocalDate.of(year, month, 1);
		LocalDate endDate = startDate.plusMonths(1);
		List<RunningRecordResponse> records = jdbcTemplate.query("""
				SELECT rr.run_record_id, rr.plan_id, rr.created_at, rr.total_distance, rr.duration, rr.pace, rr.calories,
				       EXISTS (
				           SELECT 1
				           FROM community_contents cc
				           WHERE cc.running_record_id = rr.run_record_id
				             AND cc.content_type = 'POST'
				       ) AS is_feed_linked
				FROM running_records rr
				WHERE rr.user_id = ? AND rr.created_at >= ? AND rr.created_at < ?
				ORDER BY rr.created_at DESC, rr.run_record_id DESC
				""", runningRecordRowMapper, userId, startDate, endDate);
		return attachGpsTraces(records);
	}

	public RunningRecordResponse findByRecordIdForUser(long userId, long recordId) {
		List<RunningRecordResponse> records = jdbcTemplate.query("""
				SELECT rr.run_record_id, rr.plan_id, rr.created_at, rr.total_distance, rr.duration, rr.pace, rr.calories,
				       EXISTS (
				           SELECT 1
				           FROM community_contents cc
				           WHERE cc.running_record_id = rr.run_record_id
				             AND cc.content_type = 'POST'
				       ) AS is_feed_linked
				FROM running_records rr
				WHERE rr.user_id = ? AND rr.run_record_id = ?
				""", runningRecordRowMapper, userId, recordId);
		return records.isEmpty() ? null : attachGpsTraces(records).getFirst();
	}

	public Long findOwnerUserId(long recordId) {
		List<Long> userIds = jdbcTemplate.query("""
				SELECT user_id
				FROM running_records
				WHERE run_record_id = ?
				""", (rs, rowNum) -> rs.getLong("user_id"), recordId);
		return userIds.isEmpty() ? null : userIds.getFirst();
	}

	public Long findPlanIdByRecordIdForUser(long userId, long recordId) {
		List<Long> planIds = jdbcTemplate.query("""
				SELECT plan_id
				FROM running_records
				WHERE user_id = ? AND run_record_id = ? AND plan_id IS NOT NULL
				""", (rs, rowNum) -> rs.getLong("plan_id"), userId, recordId);
		return planIds.isEmpty() ? null : planIds.getFirst();
	}

	public boolean hasRecordsForPlanIdForUser(long userId, long planId) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM running_records
				WHERE user_id = ? AND plan_id = ?
				""", Integer.class, userId, planId);
		return count != null && count > 0;
	}

	public int deleteByRecordIdForUser(long userId, long recordId) {
		return jdbcTemplate.update("DELETE FROM running_records WHERE user_id = ? AND run_record_id = ?", userId, recordId);
	}

	public Map<Long, RunningAggregate> summarizeByUsers(Collection<Long> userIds, LocalDate from, LocalDate to) {
		if (userIds == null || userIds.isEmpty()) {
			return Map.of();
		}
		List<Long> targetUserIds = userIds.stream()
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		if (targetUserIds.isEmpty()) {
			return Map.of();
		}

		String placeholders = String.join(",", java.util.Collections.nCopies(targetUserIds.size(), "?"));
		StringBuilder sql = new StringBuilder("""
				SELECT user_id, COALESCE(SUM(total_distance), 0) AS total_distance_km, COUNT(*) AS run_count
				FROM running_records
				WHERE user_id IN (%s)
				""".formatted(placeholders));
		List<Object> args = new ArrayList<>(targetUserIds);
		if (from != null) {
			sql.append(" AND created_at >= ?");
			args.add(from.atStartOfDay());
		}
		if (to != null) {
			sql.append(" AND created_at < ?");
			args.add(to.plusDays(1).atStartOfDay());
		}
		sql.append(" GROUP BY user_id");

		Map<Long, RunningAggregate> aggregates = new LinkedHashMap<>();
		jdbcTemplate.query(sql.toString(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
			long userId = rs.getLong("user_id");
			aggregates.put(userId, new RunningAggregate(
					userId,
					rs.getBigDecimal("total_distance_km"),
					rs.getLong("run_count")
			));
		}, args.toArray());
		for (Long userId : targetUserIds) {
			aggregates.putIfAbsent(userId, new RunningAggregate(userId, BigDecimal.ZERO, 0));
		}
		return aggregates;
	}

	private List<RunningRecordResponse> attachGpsTraces(List<RunningRecordResponse> records) {
		if (records == null || records.isEmpty()) {
			return List.of();
		}
		List<Long> recordIds = records.stream().map(RunningRecordResponse::runRecordId).toList();
		Map<Long, List<GpsTraceRequest>> tracesByRecordId = findGpsTracesByRecordIds(recordIds);
		return records.stream()
				.map(record -> RunningRecordResponse.record(
						record.runRecordId(),
						record.isFeedLinked(),
						record.planId(),
						record.createdAt(),
						record.totalDistance(),
						record.duration(),
						record.pace(),
						record.calories(),
						tracesByRecordId.getOrDefault(record.runRecordId(), List.of()),
						List.of()
				))
				.toList();
	}

	private Map<Long, List<GpsTraceRequest>> findGpsTracesByRecordIds(List<Long> recordIds) {
		if (recordIds == null || recordIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = String.join(",", java.util.Collections.nCopies(recordIds.size(), "?"));
		boolean includeWatchMetrics = supportsWatchMetricColumns();
		String metricColumns = includeWatchMetrics ? ", heart_rate, cadence" : "";
		String sql = """
				SELECT run_record_id, latitude, longitude, recorded_time%s
				FROM gps_traces
				WHERE run_record_id IN (%s)
				ORDER BY run_record_id ASC, recorded_time ASC, trace_id ASC
				""".formatted(metricColumns, placeholders);
		Map<Long, List<GpsTraceRequest>> tracesByRecordId = new LinkedHashMap<>();
		jdbcTemplate.query(sql, rs -> {
			long runRecordId = rs.getLong("run_record_id");
			Double heartRate = includeWatchMetrics ? nullableDouble(rs.getObject("heart_rate")) : null;
			Double cadence = includeWatchMetrics ? nullableDouble(rs.getObject("cadence")) : null;
			tracesByRecordId.computeIfAbsent(runRecordId, ignored -> new ArrayList<>()).add(new GpsTraceRequest(
					rs.getDouble("latitude"),
					rs.getDouble("longitude"),
					rs.getTimestamp("recorded_time").toLocalDateTime().format(TRACE_TIME_FORMATTER),
					heartRate,
					cadence
			));
		}, recordIds.toArray());
		return tracesByRecordId;
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
