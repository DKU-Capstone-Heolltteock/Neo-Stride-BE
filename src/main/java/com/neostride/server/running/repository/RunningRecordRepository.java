package com.neostride.server.running.repository;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
			ps.setInt(4, request.duration());
			ps.setInt(5, request.pace());
			ps.setInt(6, roundedInt(request.calories()));
			ps.setString(7, request.routeDetail());
			return ps;
		}, keyHolder);

		Number generatedId = keyHolder.getKey();
		if (generatedId == null) {
			throw new IllegalStateException("러닝 기록 ID를 생성하지 못했습니다.");
		}
		updateBadgeAndNotifyIfImproved(request.userId(), request.badge());
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
		List<RunningRecordResponse> records = jdbcTemplate.query("""
				SELECT run_record_id, plan_id, created_at, total_distance, duration, pace, calories
				FROM running_records
				WHERE user_id = ?
				ORDER BY created_at DESC, run_record_id DESC
				""", runningRecordRowMapper, userId);
		return attachGpsTraces(records);
	}

	public List<RunningRecordResponse> findByUserIdAndMonth(long userId, int year, int month) {
		LocalDate startDate = LocalDate.of(year, month, 1);
		LocalDate endDate = startDate.plusMonths(1);
		List<RunningRecordResponse> records = jdbcTemplate.query("""
				SELECT run_record_id, plan_id, created_at, total_distance, duration, pace, calories
				FROM running_records
				WHERE user_id = ? AND created_at >= ? AND created_at < ?
				ORDER BY created_at DESC, run_record_id DESC
				""", runningRecordRowMapper, userId, startDate, endDate);
		return attachGpsTraces(records);
	}

	public RunningRecordResponse findByRecordIdForUser(long userId, long recordId) {
		List<RunningRecordResponse> records = jdbcTemplate.query("""
				SELECT run_record_id, plan_id, created_at, total_distance, duration, pace, calories
				FROM running_records
				WHERE user_id = ? AND run_record_id = ?
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

	public int deleteByRecordIdForUser(long userId, long recordId) {
		return jdbcTemplate.update("DELETE FROM running_records WHERE user_id = ? AND run_record_id = ?", userId, recordId);
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


	private void updateBadgeAndNotifyIfImproved(long userId, String rawBadge) {
		String badge = normalizeBadge(rawBadge);
		if (badge == null || "NONE".equals(badge)) {
			return;
		}
		String previousBadge = currentBadge(userId);
		if (badgeRank(badge) <= badgeRank(previousBadge)) {
			return;
		}
		jdbcTemplate.update("""
			INSERT INTO community_users (user_id, community_profile_name, profile_photo, badge)
			SELECT user_id, COALESCE(community_profile_name, name), profile_photo, ?
			FROM users
			WHERE user_id = ?
			ON DUPLICATE KEY UPDATE badge = VALUES(badge)
			""", badge, userId);
		jdbcTemplate.update("""
			INSERT INTO notifications (user_id, notification_type, message, endpoint, is_read, created_at)
			VALUES (?, 'GRADE', ?, '/users/me/badge', FALSE, NOW())
			""", userId, badge + " 배지를 달성했습니다.");
	}

	private String currentBadge(long userId) {
		List<String> rows = jdbcTemplate.query("""
			SELECT COALESCE(cu.badge, 'NONE') AS badge
			FROM users u LEFT JOIN community_users cu ON cu.user_id = u.user_id
			WHERE u.user_id = ?
			""", (rs, rowNum) -> rs.getString("badge"), userId);
		return rows == null || rows.isEmpty() || rows.getFirst() == null ? "NONE" : rows.getFirst();
	}

	private static String normalizeBadge(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
			case "BRONZE" -> "BRONZE";
			case "SILVER" -> "SILVER";
			case "GOLD" -> "GOLD";
			case "PLATINUM" -> "PLATINUM";
			case "DIAMOND" -> "DIAMOND";
			case "MASTER" -> "MASTER";
			case "CHALLENGER" -> "CHALLENGER";
			default -> "NONE";
		};
	}

	private static int badgeRank(String badge) {
		return switch (badge == null ? "NONE" : badge.trim().toUpperCase(java.util.Locale.ROOT)) {
			case "BRONZE" -> 1;
			case "SILVER" -> 2;
			case "GOLD" -> 3;
			case "PLATINUM" -> 4;
			case "DIAMOND" -> 5;
			case "MASTER" -> 6;
			case "CHALLENGER" -> 7;
			default -> 0;
		};
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
