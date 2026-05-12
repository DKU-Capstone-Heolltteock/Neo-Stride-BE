package com.neostride.server.running.service;

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
