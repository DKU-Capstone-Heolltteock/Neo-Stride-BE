package com.neostride.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.neostride.server.community.dto.FeedCursorResponse;
import com.neostride.server.community.dto.FeedPageResponse;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.storage.ImageUrlResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

class ImageUrlResponseAdviceTest {
	@TempDir
	Path tempDir;

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
	void beforeBodyWrite_rewritesFeedPageImageUrls() {
		FeedUploadResponse feed = new FeedUploadResponse(1L, "/uploads/profile/me.jpg", "neo",
				"2026-05-26T00:00:00", "title", "content", 0, 0, 0, "1.00 km", null, null, true,
				"/uploads/routes/route.png", List.of("/uploads/community/photo.jpg"));
		FeedPageResponse response = new FeedPageResponse(List.of(feed), new FeedCursorResponse("2026-05-26T00:00:00", 1L), true);

		FeedPageResponse result = (FeedPageResponse) advice.beforeBodyWrite(response, null, null, null, null, null);

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().getFirst().profileImageUrl()).isEqualTo("https://api.neostride.test/uploads/profile/me.jpg");
		assertThat(result.items().getFirst().routeMapImageUri()).isEqualTo("https://api.neostride.test/uploads/routes/route.png");
		assertThat(result.items().getFirst().imageUrls()).containsExactly("https://api.neostride.test/uploads/community/photo.jpg");
		assertThat(result.nextCursor()).isEqualTo(response.nextCursor());
	}


	@Test
	void beforeBodyWrite_usesThumbnailUrlsForFeedListGetWhenSidecarExists() throws Exception {
		Path communityDir = tempDir.resolve("community");
		Path thumbnailDir = communityDir.resolve("_thumbs");
		Files.createDirectories(thumbnailDir);
		Files.write(communityDir.resolve("photo.jpg"), new byte[] {1});
		Files.write(thumbnailDir.resolve("photo_480.jpg"), new byte[] {1});
		ImageUrlResponseAdvice thumbnailAdvice = new ImageUrlResponseAdvice(
				new ImageUrlResolver("https://api.neostride.test", tempDir, "/uploads"));
		FeedUploadResponse response = new FeedUploadResponse(1L, null, "neo",
				"2026-05-26T00:00:00", "title", "content", 0, 0, 0, "1.00 km", null, null, false,
				null, List.of("/uploads/community/photo.jpg"));
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/community/feeds");

		FeedUploadResponse result = (FeedUploadResponse) thumbnailAdvice.beforeBodyWrite(response, null, null, null,
				new ServletServerHttpRequest(servletRequest), null);

		assertThat(result.imageUrls()).containsExactly("https://api.neostride.test/uploads/community/_thumbs/photo_480.jpg");
	}

	@Test
	void beforeBodyWrite_usesWebpThumbnailUrlsOnlyWhenRequested() throws Exception {
		Path communityDir = tempDir.resolve("community");
		Path thumbnailDir = communityDir.resolve("_thumbs");
		Files.createDirectories(thumbnailDir);
		Files.write(communityDir.resolve("photo.jpg"), new byte[] {1});
		Files.write(thumbnailDir.resolve("photo_480.jpg"), new byte[] {1});
		Files.write(thumbnailDir.resolve("photo_480.webp"), new byte[] {1});
		ImageUrlResponseAdvice thumbnailAdvice = new ImageUrlResponseAdvice(
				new ImageUrlResolver("https://api.neostride.test", tempDir, "/uploads"));
		FeedUploadResponse response = new FeedUploadResponse(1L, null, "neo",
				"2026-05-26T00:00:00", "title", "content", 0, 0, 0, "1.00 km", null, null, false,
				null, List.of("/uploads/community/photo.jpg"));
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/community/feeds/page");
		servletRequest.addHeader("X-Neostride-Image-Format", "webp");

		FeedUploadResponse result = (FeedUploadResponse) thumbnailAdvice.beforeBodyWrite(response, null, null, null,
				new ServletServerHttpRequest(servletRequest), null);

		assertThat(result.imageUrls()).containsExactly("https://api.neostride.test/uploads/community/_thumbs/photo_480.webp");
	}

	@Test
	void beforeBodyWrite_rewritesProfilePhoto() {
		UserProfileResponse response = new UserProfileResponse("neo", "/uploads/profile/me.jpg", null, false, false, 0, 0, 0, 0, 0, 0);

		UserProfileResponse result = (UserProfileResponse) advice.beforeBodyWrite(response, null, null, null, null, null);

		assertThat(result.profilePhoto()).isEqualTo("https://api.neostride.test/uploads/profile/me.jpg");
	}
}
