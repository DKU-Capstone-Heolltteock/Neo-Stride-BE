package com.neostride.server.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void storeImage_writesFileWithUuidNameAndReturnsPublicUrl() throws Exception {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		byte[] imageBytes = imageBytes("png");
		MockMultipartFile file = new MockMultipartFile("image", "my photo.png", "image/png", imageBytes);

		String url = storageService.storeImage(file, "community");

		assertThat(url).startsWith("/uploads/community/").endsWith(".png");
		assertThat(url).doesNotContain("my photo");
		Path storedPath = tempDir.resolve(url.replaceFirst("^/uploads/", ""));
		assertThat(Files.exists(storedPath)).isTrue();
		assertThat(Files.readAllBytes(storedPath)).containsExactly(imageBytes);
	}

	@Test
	void storeImage_rejectsEmptyFile() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		MockMultipartFile file = new MockMultipartFile("image", "empty.png", "image/png", new byte[0]);

		assertThatThrownBy(() -> storageService.storeImage(file, "community"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("빈 파일");
	}

	@Test
	void storeImage_rejectsUnsupportedMimeTypeAndExtension() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		MockMultipartFile file = new MockMultipartFile("image", "shell.gif", "image/gif", new byte[] {'G', 'I', 'F'});

		assertThatThrownBy(() -> storageService.storeImage(file, "community"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("지원하지 않는 이미지 형식");
	}

	@Test
	void storeImage_rejectsContentThatDoesNotMatchMagicBytes() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		MockMultipartFile file = new MockMultipartFile("image", "fake.png", "image/png", new byte[] {'n', 'o', 't', 'p', 'n', 'g'});

		assertThatThrownBy(() -> storageService.storeImage(file, "community"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미지 파일 내용");
	}

	@Test
	void storeImage_acceptsAndroidGalleryFileWithoutExtensionWhenMimeAndBytesAreValid() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		MockMultipartFile file = new MockMultipartFile("images", "1000001234", "image/jpeg", imageBytes("jpg"));

		String stored = storageService.storeImage(file, "community");

		assertThat(stored).startsWith("/uploads/community/").endsWith(".jpg");
	}

	@Test
	void storeImage_acceptsAndroidGalleryImageWhenDeclaredMimeDiffersFromMagicBytes() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		MockMultipartFile file = new MockMultipartFile("images", "1000001234", "image/jpeg", imageBytes("png"));

		String stored = storageService.storeImage(file, "community");

		assertThat(stored).startsWith("/uploads/community/").endsWith(".png");
	}

	@Test
	void storeImage_rejectsPngThatHasOnlyHeaderChunks() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		byte[] truncatedPng = new byte[] {
				(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10,
				0, 0, 0, 13, 'I', 'H', 'D', 'R',
				0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0,
				(byte) 0x90, 'w', 'S', (byte) 0xde
		};
		MockMultipartFile file = new MockMultipartFile("images", "tiny.png", "image/png", truncatedPng);

		assertThatThrownBy(() -> storageService.storeImage(file, "community"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미지 파일을 읽을 수 없습니다");
	}

	@Test
	void storeImage_acceptsIphoneHeicImage() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		MockMultipartFile file = new MockMultipartFile("images", "IMG_0001.HEIC", "image/heic", isoBaseMediaFile("heic"));

		String stored = storageService.storeImage(file, "community");

		assertThat(stored).startsWith("/uploads/community/").endsWith(".heic");
	}

	@Test
	void storeImage_acceptsHeifImageWithOctetStreamMime() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		MockMultipartFile file = new MockMultipartFile("images", "IMG_0002", "application/octet-stream", isoBaseMediaFile("mif1"));

		String stored = storageService.storeImage(file, "community");

		assertThat(stored).startsWith("/uploads/community/").endsWith(".heif");
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

	private static byte[] isoBaseMediaFile(String majorBrand) {
		byte[] bytes = new byte[24];
		bytes[3] = 24;
		bytes[4] = 'f';
		bytes[5] = 't';
		bytes[6] = 'y';
		bytes[7] = 'p';
		for (int i = 0; i < 4; i++) {
			bytes[8 + i] = (byte) majorBrand.charAt(i);
			bytes[16 + i] = (byte) majorBrand.charAt(i);
		}
		return bytes;
	}
}
