package com.neostride.server.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
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
	void storeImage_appliesJpegExifOrientationBeforeSaving() throws Exception {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		byte[] imageBytes = jpegWithExifOrientation(imageBytes("jpg", 2, 3), 6);
		MockMultipartFile file = new MockMultipartFile("image", "portrait.jpg", "image/jpeg", imageBytes);

		String url = storageService.storeImage(file, "profile");

		Path storedPath = tempDir.resolve(url.replaceFirst("^/uploads/", ""));
		BufferedImage storedImage = ImageIO.read(storedPath.toFile());
		assertThat(storedImage.getWidth()).isEqualTo(3);
		assertThat(storedImage.getHeight()).isEqualTo(2);
	}

	@Test
	void storeImage_createsThumbnailForJpegUpload() throws Exception {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		byte[] imageBytes = imageBytes("jpg", 1600, 900);
		MockMultipartFile file = new MockMultipartFile("image", "wide.jpg", "image/jpeg", imageBytes);

		String url = storageService.storeImage(file, "community");

		Path storedPath = tempDir.resolve(url.replaceFirst("^/uploads/", ""));
		String filename = storedPath.getFileName().toString();
		String basename = filename.substring(0, filename.lastIndexOf('.'));
		Path thumbnail = storedPath.getParent().resolve("_thumbs").resolve(basename + "_480.jpg");
		assertThat(Files.exists(thumbnail)).isTrue();
		assertThat(Files.size(thumbnail)).isGreaterThan(0);
		BufferedImage thumbnailImage = ImageIO.read(thumbnail.toFile());
		assertThat(thumbnailImage.getWidth()).isEqualTo(480);
		assertThat(thumbnailImage.getHeight()).isEqualTo(270);
	}

	@Test
	void storeImage_schedulesThumbnailAfterOriginalFileIsStored() throws Exception {
		AtomicReference<Runnable> thumbnailTask = new AtomicReference<>();
		StorageService storageService = new StorageService(tempDir, "/uploads", command -> {
			if (!thumbnailTask.compareAndSet(null, command)) {
				throw new IllegalStateException("unexpected extra thumbnail task");
			}
		});
		byte[] imageBytes = imageBytes("jpg", 1600, 900);
		MockMultipartFile file = new MockMultipartFile("image", "wide.jpg", "image/jpeg", imageBytes);

		String url = storageService.storeImage(file, "community");

		Path storedPath = tempDir.resolve(url.replaceFirst("^/uploads/", ""));
		String filename = storedPath.getFileName().toString();
		String basename = filename.substring(0, filename.lastIndexOf('.'));
		Path thumbnail = storedPath.getParent().resolve("_thumbs").resolve(basename + "_480.jpg");
		assertThat(Files.exists(storedPath)).isTrue();
		assertThat(Files.exists(thumbnail)).isFalse();
		assertThat(thumbnailTask.get()).isNotNull();

		thumbnailTask.get().run();

		assertThat(Files.exists(thumbnail)).isTrue();
		assertThat(Files.size(thumbnail)).isGreaterThan(0);
	}

	@Test
	void queuedThumbnailSkipsWhenOriginalWasDeletedBeforeTaskRuns() throws Exception {
		AtomicReference<Runnable> thumbnailTask = new AtomicReference<>();
		StorageService storageService = new StorageService(tempDir, "/uploads", command -> {
			if (!thumbnailTask.compareAndSet(null, command)) {
				throw new IllegalStateException("unexpected extra thumbnail task");
			}
		});
		byte[] imageBytes = imageBytes("jpg", 1600, 900);
		MockMultipartFile file = new MockMultipartFile("image", "wide.jpg", "image/jpeg", imageBytes);

		String url = storageService.storeImage(file, "profile");
		Path storedPath = tempDir.resolve(url.replaceFirst("^/uploads/", ""));
		String filename = storedPath.getFileName().toString();
		String basename = filename.substring(0, filename.lastIndexOf('.'));
		Path thumbnail = storedPath.getParent().resolve("_thumbs").resolve(basename + "_480.jpg");

		storageService.deleteStoredImage(url);
		thumbnailTask.get().run();

		assertThat(Files.exists(storedPath)).isFalse();
		assertThat(Files.exists(thumbnail)).isFalse();
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
	void storeImage_rejectsImageDimensionsThatWouldExhaustProcessing() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		MockMultipartFile file = new MockMultipartFile("image", "huge.png", "image/png", pngHeaderOnly(25_001, 1_000));

		assertThatThrownBy(() -> storageService.storeImage(file, "profile"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미지 크기");
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
	void storeImage_rejectsPngDimensionsBeforeDecodingRaster() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		MockMultipartFile file = new MockMultipartFile("images", "huge.png", "image/png", pngHeaderOnly(9000, 9000));

		assertThatThrownBy(() -> storageService.storeImage(file, "community"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미지 크기가 너무 큽니다");
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

	@Test
	void storeImage_rejectsHeicWhenFtypBoxSizeIsMalformed() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		byte[] bytes = new byte[] {
				'<', 'h', '1', ' ', 'f', 't', 'y', 'p',
				0, 0, 0, 0, 0, 0, 0, 0,
				'h', 'e', 'i', 'c'
		};
		MockMultipartFile file = new MockMultipartFile("images", "payload.heic", "image/heic", bytes);

		assertThatThrownBy(() -> storageService.storeImage(file, "community"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미지 파일 내용");
	}

	@Test
	void storeImage_rejectsHeicBrandOutsideFtypBox() {
		StorageService storageService = new StorageService(tempDir, "/uploads");
		byte[] bytes = new byte[20];
		bytes[3] = 16;
		bytes[4] = 'f';
		bytes[5] = 't';
		bytes[6] = 'y';
		bytes[7] = 'p';
		bytes[8] = 'i';
		bytes[9] = 's';
		bytes[10] = 'o';
		bytes[11] = 'm';
		bytes[16] = 'h';
		bytes[17] = 'e';
		bytes[18] = 'i';
		bytes[19] = 'c';
		MockMultipartFile file = new MockMultipartFile("images", "payload.heic", "image/heic", bytes);

		assertThatThrownBy(() -> storageService.storeImage(file, "community"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미지 파일 내용");
	}

	private static byte[] pngHeaderOnly(int width, int height) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
		writePngChunk(output, "IHDR", new byte[] {
				(byte) (width >>> 24), (byte) (width >>> 16), (byte) (width >>> 8), (byte) width,
				(byte) (height >>> 24), (byte) (height >>> 16), (byte) (height >>> 8), (byte) height,
				8, 2, 0, 0, 0
		});
		return output.toByteArray();
	}

	private static void writePngChunk(ByteArrayOutputStream output, String type, byte[] data) {
		output.write((data.length >>> 24) & 0xff);
		output.write((data.length >>> 16) & 0xff);
		output.write((data.length >>> 8) & 0xff);
		output.write(data.length & 0xff);
		byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		output.writeBytes(typeBytes);
		output.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		long value = crc.getValue();
		output.write((int) ((value >>> 24) & 0xff));
		output.write((int) ((value >>> 16) & 0xff));
		output.write((int) ((value >>> 8) & 0xff));
		output.write((int) (value & 0xff));
	}

	private static byte[] imageBytes(String format) {
		return imageBytes(format, 1, 1);
	}

	private static byte[] imageBytes(String format, int width, int height) {
		try {
			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			if (!ImageIO.write(image, format, output)) {
				throw new IllegalStateException("No ImageIO writer for " + format);
			}
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static byte[] jpegWithExifOrientation(byte[] jpeg, int orientation) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.write(jpeg, 0, 2);
		byte[] segment = exifOrientationSegment(orientation);
		output.write(segment, 0, segment.length);
		output.write(jpeg, 2, jpeg.length - 2);
		return output.toByteArray();
	}

	private static byte[] exifOrientationSegment(int orientation) {
		byte[] tiff = new byte[] {
				'M', 'M', 0, 42, 0, 0, 0, 8,
				0, 1,
				1, 18, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
				0, 0, 0, 0
		};
		int payloadLength = 6 + tiff.length;
		int app1Length = payloadLength + 2;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.write(0xff);
		output.write(0xe1);
		output.write((app1Length >>> 8) & 0xff);
		output.write(app1Length & 0xff);
		output.write('E');
		output.write('x');
		output.write('i');
		output.write('f');
		output.write(0);
		output.write(0);
		output.write(tiff, 0, tiff.length);
		return output.toByteArray();
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
