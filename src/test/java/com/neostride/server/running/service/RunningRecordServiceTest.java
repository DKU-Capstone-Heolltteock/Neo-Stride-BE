package com.neostride.server.running.service;

import com.neostride.server.coaching.service.CoachingService;
import com.neostride.server.running.dto.GpsTraceRequest;
import com.neostride.server.running.dto.RunningRecordRequest;
import com.neostride.server.running.repository.RunningRecordRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
		CoachingService coachingService = mock(CoachingService.class);
		RunningRecordService coachingAwareService = new RunningRecordService(repository, coachingService);
		GpsTraceRequest trace = new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", null, null);
		RunningRecordRequest request = new RunningRecordRequest(
				7L,
				20L,
				new BigDecimal("5.23"),
				new BigDecimal("1800"),
				new BigDecimal("392"),
				new BigDecimal("310.56"),
				"",
				List.of(trace)
		);
		when(repository.insertRunningRecord(request)).thenReturn(12L);

		long recordId = coachingAwareService.save(request);

		assertThat(recordId).isEqualTo(12L);
		verify(repository).insertGpsTraces(12L, List.of(trace));
		verify(coachingService).completePlanWithRunningRecord(7L, 20L, new BigDecimal("5.23"), 1800, new BigDecimal("392"));
	}

	@Test
	void saveDoesNotTouchCoachingWhenPlanIdIsMissing() {
		CoachingService coachingService = mock(CoachingService.class);
		RunningRecordService coachingAwareService = new RunningRecordService(repository, coachingService);
		RunningRecordRequest request = requestWithTraces(List.of(new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", null, null)));
		when(repository.insertRunningRecord(request)).thenReturn(13L);

		coachingAwareService.save(request);

		verifyNoInteractions(coachingService);
	}

	private RunningRecordRequest requestWithTraces(List<GpsTraceRequest> traces) {
		return new RunningRecordRequest(
				7L,
				null,
				new BigDecimal("5.23"),
				new BigDecimal("1800"),
				new BigDecimal("5.77"),
				new BigDecimal("310.56"),
				"",
				traces
		);
	}
}
