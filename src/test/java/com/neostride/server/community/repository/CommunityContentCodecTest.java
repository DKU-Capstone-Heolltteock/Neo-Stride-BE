package com.neostride.server.community.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommunityContentCodecTest {

	@Test
	void feedContentRoundTripsLegacyMetricsPayload() {
		String encoded = CommunityContentCodec.encodeFeedContent(
				"title",
				"body",
				"route.png",
				new BigDecimal("3.2"),
				"20:00",
				"6'15\""
		);

		DecodedFeedContent decoded = CommunityContentCodec.decodeFeedContent(encoded);

		assertThat(encoded).contains("---NEOSTRIDE-METRICS---");
		assertThat(decoded.title()).isEqualTo("title");
		assertThat(decoded.content()).isEqualTo("body");
		assertThat(decoded.routeMapImageUri()).isEqualTo("route.png");
		assertThat(decoded.distance()).isEqualTo("3.2");
		assertThat(decoded.duration()).isEqualTo("20:00");
		assertThat(decoded.pace()).isEqualTo("6'15\"");
	}

	@Test
	void decodeFeedContentPrefersStructuredColumnsWhenPresent() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString("title")).thenReturn("structured title");
		when(rs.getString("body_text")).thenReturn("structured body");
		when(rs.getString("route_map_image_url")).thenReturn("structured-route.png");
		when(rs.getBigDecimal("distance_km")).thenReturn(new BigDecimal("4.50"));
		when(rs.getString("running_time_text")).thenReturn("25:30");
		when(rs.getString("pace_text")).thenReturn("5:40/km");
		when(rs.getString("content_text")).thenReturn("legacy title\n---NEOSTRIDE-FEED---\nlegacy body");

		DecodedFeedContent decoded = CommunityContentCodec.decodeFeedContent(rs);

		assertThat(decoded.title()).isEqualTo("structured title");
		assertThat(decoded.content()).isEqualTo("structured body");
		assertThat(decoded.routeMapImageUri()).isEqualTo("structured-route.png");
		assertThat(decoded.distance()).isEqualTo("4.5");
		assertThat(decoded.duration()).isEqualTo("25:30");
		assertThat(decoded.pace()).isEqualTo("5:40/km");
	}

	@Test
	void imageEncodingTrimsBlankValuesAndRoundTripsDelimiterPayload() {
		String encoded = CommunityContentCodec.encodeImages(List.of(
				" /uploads/community/a.jpg ",
				"",
				"/uploads/community/b.jpg"
		));

		assertThat(encoded).contains("---NEOSTRIDE-IMAGE---");
		assertThat(CommunityContentCodec.decodeImages(encoded))
				.containsExactly("/uploads/community/a.jpg", "/uploads/community/b.jpg");
	}

	@Test
	void tipContentRoundTripsLegacyCourseAddressPayload() {
		String encoded = CommunityContentCodec.encodeTipContent("course", "body", "route.png", "Seoul Forest");

		String[] decoded = CommunityContentCodec.decodeTipContent(encoded);

		assertThat(encoded).contains("---NEOSTRIDE-ADDR---");
		assertThat(decoded).containsExactly("course", "body", "route.png", "Seoul Forest");
	}

	@Test
	void paceAndDurationParsersKeepAndroidTextCompatibility() {
		assertThat(CommunityContentCodec.parseDurationSeconds("1:02:03")).isEqualTo(3723);
		assertThat(CommunityContentCodec.parsePaceSeconds("5:37/km")).isEqualTo(337);
		assertThat(CommunityContentCodec.parsePaceSeconds("6'15\"")).isEqualTo(375);
	}
}
