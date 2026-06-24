package com.neostride.server.running.service;

import com.neostride.server.coaching.api.CoachingPlanProgressPort;
import com.neostride.server.community.api.BadgeProgressPort;
import com.neostride.server.running.dto.GpsTraceRequest;
import com.neostride.server.running.dto.RunningRecordRequest;
import com.neostride.server.running.dto.RunningRecordResponse;
import com.neostride.server.running.repository.RunningRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunningRecordService {

	public enum DeleteResult {
		DELETED,
		NOT_FOUND
	}

	private static final DateTimeFormatter TRACE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final RunningRecordRepository runningRecordRepository;
	private final CoachingPlanProgressPort coachingPlanProgressPort;
	private final BadgeProgressPort badgeProgressPort;

	@Autowired
	public RunningRecordService(RunningRecordRepository runningRecordRepository,
			CoachingPlanProgressPort coachingPlanProgressPort,
			BadgeProgressPort badgeProgressPort) {
		this.runningRecordRepository = runningRecordRepository;
		this.coachingPlanProgressPort = coachingPlanProgressPort;
		this.badgeProgressPort = badgeProgressPort;
	}

	RunningRecordService(RunningRecordRepository runningRecordRepository) {
		this(runningRecordRepository, null, null);
	}

	RunningRecordService(RunningRecordRepository runningRecordRepository, CoachingPlanProgressPort coachingPlanProgressPort) {
		this(runningRecordRepository, coachingPlanProgressPort, null);
	}

	RunningRecordService(RunningRecordRepository runningRecordRepository, BadgeProgressPort badgeProgressPort) {
		this(runningRecordRepository, null, badgeProgressPort);
	}

	@Transactional
	public long save(RunningRecordRequest request) {
		validate(request);

		long runRecordId = runningRecordRepository.insertRunningRecord(request);
		runningRecordRepository.insertGpsTraces(runRecordId, request.gpsTraces());
		if (request.planId() != null && coachingPlanProgressPort != null) {
			coachingPlanProgressPort.completePlanWithRunningRecord(
					request.userId(),
					request.planId(),
					request.totalDistance(),
					request.duration(),
					request.pace()
			);
		}
		return runRecordId;
	}

	@Transactional(readOnly = true)
	public List<RunningRecordResponse> findByUserId(long userId) {
		if (userId <= 0) {
			throw new IllegalArgumentException("user_id는 1 이상의 값이어야 합니다.");
		}
		return runningRecordRepository.findByUserId(userId);
	}

	@Transactional(readOnly = true)
	public List<RunningRecordResponse> findByUserIdAndMonth(long userId, int year, int month) {
		if (userId <= 0) {
			throw new IllegalArgumentException("user_id는 1 이상의 값이어야 합니다.");
		}
		if (year <= 0) {
			throw new IllegalArgumentException("year는 1 이상의 값이어야 합니다.");
		}
		if (month < 1 || month > 12) {
			throw new IllegalArgumentException("month는 1 이상 12 이하의 값이어야 합니다.");
		}
		return runningRecordRepository.findByUserIdAndMonth(userId, year, month);
	}

	@Transactional(readOnly = true)
	public Optional<RunningRecordResponse> findByRecordIdForUser(long userId, long recordId) {
		requirePositiveUserId(userId);
		requirePositiveRecordId(recordId);
		return Optional.ofNullable(runningRecordRepository.findByRecordIdForUser(userId, recordId));
	}

	@Transactional
	public DeleteResult deleteByRecordIdForUser(long userId, long recordId) {
		requirePositiveUserId(userId);
		requirePositiveRecordId(recordId);
		Long planId = runningRecordRepository.findPlanIdByRecordIdForUser(userId, recordId);
		if (planId != null && coachingPlanProgressPort != null) {
			coachingPlanProgressPort.lockPlanForRunningRecordDeletion(userId, planId);
		}
		if (runningRecordRepository.deleteByRecordIdForUser(userId, recordId) <= 0) {
			return DeleteResult.NOT_FOUND;
		}
		if (planId != null && coachingPlanProgressPort != null
				&& !runningRecordRepository.hasRecordsForPlanIdForUser(userId, planId)) {
			coachingPlanProgressPort.restorePlanToPendingAfterRunningRecordDeleted(userId, planId);
		}
		return DeleteResult.DELETED;
	}

	private void requirePositiveUserId(long userId) {
		if (userId <= 0) {
			throw new IllegalArgumentException("user_id는 1 이상의 값이어야 합니다.");
		}
	}

	private void requirePositiveRecordId(long recordId) {
		if (recordId <= 0) {
			throw new IllegalArgumentException("record_id는 1 이상의 값이어야 합니다.");
		}
	}

	private void validate(RunningRecordRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		if (request.userId() == null || request.userId() <= 0) {
			throw new IllegalArgumentException("user_id는 1 이상의 값이어야 합니다.");
		}
		if (request.planId() != null && request.planId() <= 0) {
			throw new IllegalArgumentException("plan_id는 null 또는 1 이상의 값이어야 합니다.");
		}
		requireNonNegative(request.totalDistance(), "total_distance");
		requireNonNegative(request.duration(), "duration");
		requireNonNegative(request.pace(), "pace");
		requireNonNegative(request.calories(), "calories");
		if (request.gpsTraces() == null || request.gpsTraces().isEmpty()) {
			throw new IllegalArgumentException("gps_traces는 1개 이상 필요합니다.");
		}
		for (GpsTraceRequest trace : request.gpsTraces()) {
			validateTrace(trace);
		}
	}

	private void validateTrace(GpsTraceRequest trace) {
		if (trace == null) {
			throw new IllegalArgumentException("gps_traces 항목은 null일 수 없습니다.");
		}
		if (trace.latitude() == null || trace.latitude() < -90 || trace.latitude() > 90) {
			throw new IllegalArgumentException("latitude는 -90 이상 90 이하의 값이어야 합니다.");
		}
		if (trace.longitude() == null || trace.longitude() < -180 || trace.longitude() > 180) {
			throw new IllegalArgumentException("longitude는 -180 이상 180 이하의 값이어야 합니다.");
		}
		parseTraceTime(trace.time());
	}

	private void requireNonNegative(BigDecimal value, String fieldName) {
		if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException(fieldName + "는 0 이상의 값이어야 합니다.");
		}
	}

	private void requireNonNegative(Integer value, String fieldName) {
		if (value == null || value < 0) {
			throw new IllegalArgumentException(fieldName + "는 0 이상의 값이어야 합니다.");
		}
	}

	private LocalDateTime parseTraceTime(String time) {
		if (time == null || time.isBlank()) {
			throw new IllegalArgumentException("time은 yyyy-MM-dd HH:mm:ss 형식이어야 합니다.");
		}
		try {
			return LocalDateTime.parse(time, TRACE_TIME_FORMATTER);
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException("time은 yyyy-MM-dd HH:mm:ss 형식이어야 합니다.");
		}
	}
}
