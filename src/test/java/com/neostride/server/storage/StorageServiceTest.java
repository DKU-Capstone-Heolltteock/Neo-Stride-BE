package com.neostride.server.storage;

import java.nio.file.Files;
import java.nio.file.Path;
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
		MockMultipartFile file = new MockMultipartFile("image", "my photo.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});

		String url = storageService.storeImage(file, "community");

		assertThat(url).startsWith("/uploads/community/").endsWith(".png");
		assertThat(url).doesNotContain("my photo");
		Path storedPath = tempDir.resolve(url.replaceFirst("^/uploads/", ""));
		assertThat(Files.exists(storedPath)).isTrue();
		assertThat(Files.readAllBytes(storedPath)).containsExactly((byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10);
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
		MockMultipartFile file = new MockMultipartFile("images", "1000001234", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0});

		String stored = storageService.storeImage(file, "community");

		assertThat(stored).startsWith("/uploads/community/").endsWith(".jpg");
	}
}
