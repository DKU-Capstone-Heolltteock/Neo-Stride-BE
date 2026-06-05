package com.neostride.server.community.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityUploadRequestJsonTest {
	private final ObjectMapper objectMapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);

	@Test
	void feedUploadRequest_allowsExplicitNullFlagsWhenUploadingPhotoOrGpsPayloads() throws Exception {
		FeedUploadRequest request = objectMapper.readValue("""
				{
				  "title": "photo feed",
				  "content": "with photo",
				  "privacy": "PUBLIC",
				  "map_visible": null,
				  "route_map_image_uri": "/uploads/routes/map.png",
				  "image_urls": ["/uploads/community/photo.png"],
				  "total_distance": 3.2,
				  "duration": "20:00",
				  "running_pace": "6'15\\\"",
				  "tag_count": null,
				  "run_record_id": null
				}
				""", FeedUploadRequest.class);

		assertThat(request.mapVisible()).isFalse();
		assertThat(request.tagCount()).isZero();
		assertThat(request.routeMapImageUri()).isEqualTo("/uploads/routes/map.png");
		assertThat(request.imageUrls()).containsExactly("/uploads/community/photo.png");
		assertThat(request.distance()).isEqualByComparingTo(new BigDecimal("3.2"));
		assertThat(request.runningTime()).isEqualTo("20:00");
		assertThat(request.pace()).isEqualTo("6'15\"");
	}

	@Test
	void tipUploadRequest_allowsExplicitNullGpsFlagAndAndroidSnakeCaseAliases() throws Exception {
		TipUploadRequest request = objectMapper.readValue("""
				{
				  "category": "COURSE",
				  "title": "gps tip",
				  "content": "with gps",
				  "gps_visible": null,
				  "route_map_image_url": "/uploads/routes/map.png",
				  "course_address": "Seoul",
				  "image_urls": ["/uploads/community/tip.png"]
				}
				""", TipUploadRequest.class);

		assertThat(request.gpsVisible()).isFalse();
		assertThat(request.routeMapImageUrl()).isEqualTo("/uploads/routes/map.png");
		assertThat(request.courseAddress()).isEqualTo("Seoul");
		assertThat(request.imageUrls()).containsExactly("/uploads/community/tip.png");
	}
}
