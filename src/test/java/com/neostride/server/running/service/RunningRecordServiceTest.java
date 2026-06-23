package com.neostride.server.running.service;

import com.neostride.server.coaching.api.CoachingPlanProgressPort;
import com.neostride.server.community.api.BadgeProgressPort;
import com.neostride.server.running.dto.GpsTraceRequest;
import com.neostride.server.running.dto.RunningRecordRequest;
import com.neostride.server.running.repository.RunningRecordRepository;
import com.neostride.server.running.service.RunningRecordService.DeleteResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RunningRecordServiceTest {

	private final RunningRecordRepository repository = mock(RunningRecordRepository.class);
	private final RunningRecordService service = new RunningRecordService(repository);

	@Test
	void saveAcceptsLegacyTracePayloadWithoutHeartRateAndCadence() {
		GpsTraceRequest legacyTrace = new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", null, null);
		RunningRecordRequest request = requestWithTraces(List.of(legacyTrace));
		when(repository.insertRunningRecord(request)).thenReturn(10L);

		long recordId = service.save(request);

		assertThat(recordId).isEqualTo(10L);
		verify(repository).insertGpsTraces(10L, List.of(legacyTrace));
	}

	@Test
	void saveAcceptsTracePayloadWithHeartRateAndCadence() {
		GpsTraceRequest watchTrace = new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", 150.0, 171.0);
		RunningRecordRequest request = requestWithTraces(List.of(watchTrace));
		when(repository.insertRunningRecord(any(RunningRecordRequest.class))).thenReturn(11L);

		long recordId = service.save(request);

		assertThat(recordId).isEqualTo(11L);
		verify(repository).insertGpsTraces(11L, List.of(watchTrace));
	}

	@Test
	void saveCompletesCoachingPlanWhenPlanIdIsPresent() {
		CoachingPlanProgressPort coachingPlanProgressPort = mock(CoachingPlanProgressPort.class);
		RunningRecordService coachingAwareService = new RunningRecordService(repository, coachingPlanProgressPort);
		GpsTraceRequest trace = new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", null, null);
		RunningRecordRequest request = new RunningRecordRequest(
				7L,
				20L,
				new BigDecimal("5.23"),
				1800,
				392,
				new BigDecimal("310.56"),
				"",
				List.of(trace)
		);
		when(repository.insertRunningRecord(request)).thenReturn(12L);

		long recordId = coachingAwareService.save(request);

		assertThat(recordId).isEqualTo(12L);
		verify(repository).insertGpsTraces(12L, List.of(trace));
		verify(coachingPlanProgressPort).completePlanWithRunningRecord(7L, 20L, new BigDecimal("5.23"), 1800, 392);
	}

	@Test
	void saveDoesNotTouchCoachingWhenPlanIdIsMissing() {
		CoachingPlanProgressPort coachingPlanProgressPort = mock(CoachingPlanProgressPort.class);
		RunningRecordService coachingAwareService = new RunningRecordService(repository, coachingPlanProgressPort);
		RunningRecordRequest request = requestWithTraces(List.of(new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", null, null)));
		when(repository.insertRunningRecord(request)).thenReturn(13L);

		coachingAwareService.save(request);

		verifyNoInteractions(coachingPlanProgressPort);
	}

	@Test
	void saveIgnoresClientSuppliedBadgeForCommunityProgress() {
		BadgeProgressPort badgeProgressPort = mock(BadgeProgressPort.class);
		RunningRecordService badgeAwareService = new RunningRecordService(repository, badgeProgressPort);
		GpsTraceRequest trace = new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", null, null);
		RunningRecordRequest request = new RunningRecordRequest(
				7L,
				null,
				new BigDecimal("5.23"),
				1800,
				392,
				new BigDecimal("310.56"),
				"",
				List.of(trace),
				"GOLD"
		);
		when(repository.insertRunningRecord(request)).thenReturn(14L);

		long recordId = badgeAwareService.save(request);

		assertThat(recordId).isEqualTo(14L);
		verify(repository).insertGpsTraces(14L, List.of(trace));
		verifyNoInteractions(badgeProgressPort);
	}

	@Test
	void deleteByRecordIdForUserDeletesOwnedRecord() {
		when(repository.findOwnerUserId(10L)).thenReturn(7L);
		when(repository.deleteByRecordIdForUser(7L, 10L)).thenReturn(1);

		DeleteResult result = service.deleteByRecordIdForUser(7L, 10L);

		assertThat(result).isEqualTo(DeleteResult.DELETED);
		verify(repository).deleteByRecordIdForUser(7L, 10L);
	}

	@Test
	void deleteByRecordIdForUserRestoresCoachingPlanToPendingWhenDeletedRecordHasPlanId() {
		CoachingPlanProgressPort coachingPlanProgressPort = mock(CoachingPlanProgressPort.class);
		RunningRecordService coachingAwareService = new RunningRecordService(repository, coachingPlanProgressPort);
		when(repository.findOwnerUserId(10L)).thenReturn(7L);
		when(repository.findPlanIdByRecordIdForUser(7L, 10L)).thenReturn(20L);
		when(repository.deleteByRecordIdForUser(7L, 10L)).thenReturn(1);

		DeleteResult result = coachingAwareService.deleteByRecordIdForUser(7L, 10L);

		assertThat(result).isEqualTo(DeleteResult.DELETED);
		verify(repository).deleteByRecordIdForUser(7L, 10L);
		verify(coachingPlanProgressPort).restorePlanToPendingAfterRunningRecordDeleted(7L, 20L);
	}

	@Test
	void deleteByRecordIdForUserReturnsNotFoundWhenRecordDoesNotExist() {
		when(repository.findOwnerUserId(10L)).thenReturn(null);

		DeleteResult result = service.deleteByRecordIdForUser(7L, 10L);

		assertThat(result).isEqualTo(DeleteResult.NOT_FOUND);
		verify(repository, never()).deleteByRecordIdForUser(7L, 10L);
	}

	@Test
	void deleteByRecordIdForUserReturnsForbiddenWhenRecordBelongsToAnotherUser() {
		when(repository.findOwnerUserId(10L)).thenReturn(8L);

		DeleteResult result = service.deleteByRecordIdForUser(7L, 10L);

		assertThat(result).isEqualTo(DeleteResult.FORBIDDEN);
		verify(repository, never()).deleteByRecordIdForUser(7L, 10L);
	}

	@Test
	void deleteByRecordIdForUserReturnsNotFoundWhenDeleteFindsNoRows() {
		when(repository.findOwnerUserId(10L)).thenReturn(7L);
		when(repository.deleteByRecordIdForUser(7L, 10L)).thenReturn(0);

		DeleteResult result = service.deleteByRecordIdForUser(7L, 10L);

		assertThat(result).isEqualTo(DeleteResult.NOT_FOUND);
	}

	private RunningRecordRequest requestWithTraces(List<GpsTraceRequest> traces) {
		return new RunningRecordRequest(
				7L,
				null,
				new BigDecimal("5.23"),
				1800,
				346,
				new BigDecimal("310.56"),
				"",
				traces
		);
	}
}
