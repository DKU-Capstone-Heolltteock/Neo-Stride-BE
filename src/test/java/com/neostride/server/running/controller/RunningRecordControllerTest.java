package com.neostride.server.running.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.running.dto.GpsTraceRequest;
import com.neostride.server.running.dto.RunningRecordResponse;
import com.neostride.server.running.service.RunningRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunningRecordControllerTest {

	private static final String AUTHORIZATION = "Bearer access-token";

	private final RunningRecordService service = mock(RunningRecordService.class);
	private final AuthenticatedUserService authenticatedUserService = mock(AuthenticatedUserService.class);
	private final RunningRecordController controller = new RunningRecordController(service, authenticatedUserService);

	@Test
	void fetchUserRecords_returnsOkResponse() {
		authenticate();
		when(service.findByUserId(1L)).thenReturn(List.of(RunningRecordResponse.record(10L, "2026-04-28T14:30:00", "3.25", 1240, new BigDecimal("6.36"), 235, List.of(), List.of())));

		var response = controller.fetchUserRecords(AUTHORIZATION, 1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).hasSize(1);
	}

	@Test
	void getMonthlyRecords_returnsOkResponse() {
		authenticate();
		when(service.findByUserIdAndMonth(1L, 2026, 4)).thenReturn(List.of(RunningRecordResponse.record(10L, "2026-04-28T14:30:00", "3.25", 1240, new BigDecimal("6.36"), 235, List.of(), List.of())));

		var response = controller.getMonthlyRecords(AUTHORIZATION, 2026, 4);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).hasSize(1);
	}

	@Test
	void getRecordDetail_returnsOkResponseWithTraceHeartRateAndCadence() throws Exception {
		GpsTraceRequest trace = new GpsTraceRequest(37.5665, 126.978, "2026-05-12 18:00:00", 150.0, 171.0);
		authenticate();
		when(service.findByRecordIdForUser(1L, 10L)).thenReturn(Optional.of(RunningRecordResponse.record(10L, "2026-04-28T14:30:00", "3.25", 1240, new BigDecimal("6.36"), 235, List.of(trace), List.of())));

		var response = controller.getRecordDetail(AUTHORIZATION, 10L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().runRecordId()).isEqualTo(10L);
		String json = new ObjectMapper().writeValueAsString(response.getBody());
		assertThat(json).contains("\"heart_rate\":150.0");
		assertThat(json).contains("\"cadence\":171.0");
	}

	@Test
	void getRecordDetail_serializesPaceWithSecondPrecision() throws Exception {
		authenticate();
		when(service.findByRecordIdForUser(1L, 10L)).thenReturn(Optional.of(RunningRecordResponse.record(
				10L, "2026-04-28T14:30:00", "3.25", 1240, new BigDecimal("5.70"), 235, List.of(), List.of())));

		var response = controller.getRecordDetail(AUTHORIZATION, 10L);

		String json = new ObjectMapper().writeValueAsString(response.getBody());
		assertThat(json).contains("\"pace\":5.70");
	}

	@Test
	void getRecordDetail_returnsNotFoundWhenRecordDoesNotExist() {
		authenticate();
		when(service.findByRecordIdForUser(1L, 999L)).thenReturn(Optional.empty());

		var response = controller.getRecordDetail(AUTHORIZATION, 999L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private void authenticate() {
		when(authenticatedUserService.requireUserId(AUTHORIZATION)).thenReturn(1L);
	}
}
