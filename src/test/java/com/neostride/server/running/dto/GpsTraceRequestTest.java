package com.neostride.server.running.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GpsTraceRequestTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void deserializesLegacyTracePayloadWithoutHeartRateAndCadence() throws Exception {
		GpsTraceRequest trace = objectMapper.readValue("""
				{
				  "latitude": 37.5665,
				  "longitude": 126.978,
				  "time": "2026-05-12 18:00:00"
				}
				""", GpsTraceRequest.class);

		assertThat(trace.latitude()).isEqualTo(37.5665);
		assertThat(trace.longitude()).isEqualTo(126.978);
		assertThat(trace.time()).isEqualTo("2026-05-12 18:00:00");
		assertThat(trace.heartRate()).isNull();
		assertThat(trace.cadence()).isNull();
	}

	@Test
	void deserializesAndSerializesWatchTracePayloadWithSnakeCaseHeartRateAndCadence() throws Exception {
		GpsTraceRequest trace = objectMapper.readValue("""
				{
				  "latitude": 37.5665,
				  "longitude": 126.978,
				  "time": "2026-05-12 18:00:00",
				  "heart_rate": 150.0,
				  "cadence": 171.0
				}
				""", GpsTraceRequest.class);

		assertThat(trace.heartRate()).isEqualTo(150.0);
		assertThat(trace.cadence()).isEqualTo(171.0);

		String json = objectMapper.writeValueAsString(trace);
		assertThat(json).contains("\"heart_rate\":150.0");
		assertThat(json).contains("\"cadence\":171.0");
	}
}
