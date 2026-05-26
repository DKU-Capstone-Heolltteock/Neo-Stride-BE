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
	void toPublicUrl_dropsMissingAndBrokenStoredUploadPaths() throws Exception {
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
		assertThat(resolver.toPublicUrl("/uploads/community/broken.png")).isNull();
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
