package com.neostride.server.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ImageUrlResolverTest {
	@TempDir
	Path tempDir;

	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void toPublicUrl_usesConfiguredBaseUrlForUploadPaths() {
		ImageUrlResolver resolver = new ImageUrlResolver("https://api.neostride.test/");

		String result = resolver.toPublicUrl("/uploads/profile/profile-id.jpg");

		assertThat(result).isEqualTo("https://api.neostride.test/uploads/profile/profile-id.jpg");
	}

	@Test
	void toPublicUrl_usesCurrentRequestHostWhenBaseUrlIsNotConfigured() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/community/feeds");
		request.setScheme("http");
		request.setServerName("10.0.2.2");
		request.setServerPort(8080);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
		ImageUrlResolver resolver = new ImageUrlResolver("");

		String result = resolver.toPublicUrl("/uploads/community/feed-id.jpg");

		assertThat(result).isEqualTo("http://10.0.2.2:8080/uploads/community/feed-id.jpg");
	}

	@Test
	void toPublicUrls_keepsRemoteUrlsAndDropsDeviceLocalUris() {
		ImageUrlResolver resolver = new ImageUrlResolver("https://api.neostride.test");

		List<String> result = resolver.toPublicUrls(List.of(
				"/uploads/community/feed-id.jpg",
				"https://cdn.example.test/photo.jpg",
				"content://com.android.providers.media.documents/document/image%3A1000026860",
				"file:///cache/route.png"
		));

		assertThat(result).containsExactly(
				"https://api.neostride.test/uploads/community/feed-id.jpg",
				"https://cdn.example.test/photo.jpg"
		);
	}

	@Test
	void toPublicThumbnailUrl_usesSidecarWhenPresentAndFallsBackToOriginal() throws Exception {
		Path communityDir = tempDir.resolve("community");
		Path thumbnailDir = communityDir.resolve("_thumbs");
		Files.createDirectories(thumbnailDir);
		Files.write(communityDir.resolve("feed-id.jpg"), imageBytes("jpg"));
		Files.write(thumbnailDir.resolve("feed-id_480.jpg"), imageBytes("jpg"));
		Files.write(communityDir.resolve("without-thumb.jpg"), imageBytes("jpg"));
		ImageUrlResolver resolver = new ImageUrlResolver("https://api.neostride.test", tempDir, "/uploads");

		assertThat(resolver.toPublicThumbnailUrl("/uploads/community/feed-id.jpg"))
				.isEqualTo("https://api.neostride.test/uploads/community/_thumbs/feed-id_480.jpg");
		assertThat(resolver.toPublicThumbnailUrl("/uploads/community/without-thumb.jpg"))
				.isEqualTo("https://api.neostride.test/uploads/community/without-thumb.jpg");
	}

	@Test
	void toPublicThumbnailUrl_prefersWebpSidecarWhenRequested() throws Exception {
		Path communityDir = tempDir.resolve("community");
		Path thumbnailDir = communityDir.resolve("_thumbs");
		Files.createDirectories(thumbnailDir);
		Files.write(communityDir.resolve("feed-id.jpg"), imageBytes("jpg"));
		Files.write(thumbnailDir.resolve("feed-id_480.jpg"), imageBytes("jpg"));
		Files.write(thumbnailDir.resolve("feed-id_480.webp"), new byte[] {1});
		ImageUrlResolver resolver = new ImageUrlResolver("https://api.neostride.test", tempDir, "/uploads");

		assertThat(resolver.toPublicThumbnailUrl("/uploads/community/feed-id.jpg", true))
				.isEqualTo("https://api.neostride.test/uploads/community/_thumbs/feed-id_480.webp");
		assertThat(resolver.toPublicThumbnailUrl("/uploads/community/feed-id.jpg", false))
				.isEqualTo("https://api.neostride.test/uploads/community/_thumbs/feed-id_480.jpg");
	}

	@Test
	void toPublicUrl_cachesStoredUploadReadabilityForConfiguredTtl() throws Exception {
		Path communityDir = tempDir.resolve("community");
		Files.createDirectories(communityDir);
		Path upload = communityDir.resolve("cached.png");
		ImageUrlResolver resolver = new ImageUrlResolver("https://api.neostride.test", tempDir, "/uploads", 60_000, 16);

		assertThat(resolver.toPublicUrl("/uploads/community/cached.png")).isNull();

		Files.write(upload, imageBytes("png"));

		assertThat(resolver.toPublicUrl("/uploads/community/cached.png")).isNull();
	}

	@Test
	void toPublicUrl_allowsDisablingStoredUploadReadabilityCache() throws Exception {
		Path communityDir = tempDir.resolve("community");
		Files.createDirectories(communityDir);
		Path upload = communityDir.resolve("uncached.png");
		ImageUrlResolver resolver = new ImageUrlResolver("https://api.neostride.test", tempDir, "/uploads", 0, 16);

		assertThat(resolver.toPublicUrl("/uploads/community/uncached.png")).isNull();

		Files.write(upload, imageBytes("png"));

		assertThat(resolver.toPublicUrl("/uploads/community/uncached.png"))
				.isEqualTo("https://api.neostride.test/uploads/community/uncached.png");
	}

	@Test
	void toPublicUrl_dropsMissingStoredUploadPathsWithoutDecodingImages() throws Exception {
		Path communityDir = tempDir.resolve("community");
		Files.createDirectories(communityDir);
		Files.write(communityDir.resolve("valid.png"), imageBytes("png"));
		Files.write(communityDir.resolve("broken.png"), new byte[] {
				(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10,
				0, 0, 0, 13, 'I', 'H', 'D', 'R',
				0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0,
				(byte) 0x90, 'w', 'S', (byte) 0xde
		});
		ImageUrlResolver resolver = new ImageUrlResolver("https://api.neostride.test", tempDir, "/uploads");

		assertThat(resolver.toPublicUrl("/uploads/community/valid.png"))
				.isEqualTo("https://api.neostride.test/uploads/community/valid.png");
		assertThat(resolver.toPublicUrl("/uploads/community/broken.png"))
				.isEqualTo("https://api.neostride.test/uploads/community/broken.png");
		assertThat(resolver.toPublicUrl("/uploads/community/missing.png")).isNull();
	}

	private static byte[] imageBytes(String format) {
		try {
			BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			if (!ImageIO.write(image, format, output)) {
				throw new IllegalStateException("No ImageIO writer for " + format);
			}
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
