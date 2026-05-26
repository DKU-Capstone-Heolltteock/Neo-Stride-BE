package com.neostride.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.storage.ImageUrlResolver;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageUrlResponseAdviceTest {
	private final ImageUrlResponseAdvice advice = new ImageUrlResponseAdvice(
			new ImageUrlResolver("https://api.neostride.test"));

	@Test
	void beforeBodyWrite_rewritesFeedImageUrls() {
		FeedUploadResponse response = new FeedUploadResponse(1L, "/uploads/profile/me.jpg", "neo",
				"2026-05-26T00:00:00", "title", "content", 0, 0, 0, "1.00 km", null, null, true,
				"/uploads/routes/route.png", List.of("/uploads/community/photo.jpg", "content://old-local-photo"));

		FeedUploadResponse result = (FeedUploadResponse) advice.beforeBodyWrite(response, null, null, null, null, null);

		assertThat(result.profileImageUrl()).isEqualTo("https://api.neostride.test/uploads/profile/me.jpg");
		assertThat(result.routeMapImageUri()).isEqualTo("https://api.neostride.test/uploads/routes/route.png");
		assertThat(result.imageUrls()).containsExactly("https://api.neostride.test/uploads/community/photo.jpg");
	}

	@Test
	void beforeBodyWrite_rewritesProfilePhoto() {
		UserProfileResponse response = new UserProfileResponse("neo", "/uploads/profile/me.jpg", null, 0, 0, 0, 0, 0, 0);

		UserProfileResponse result = (UserProfileResponse) advice.beforeBodyWrite(response, null, null, null, null, null);

		assertThat(result.profilePhoto()).isEqualTo("https://api.neostride.test/uploads/profile/me.jpg");
	}
}
