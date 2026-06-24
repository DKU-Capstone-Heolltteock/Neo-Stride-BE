package com.neostride.server.storage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {
	private static final Logger log = LoggerFactory.getLogger(StorageService.class);
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
	private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp",
			"image/heic", "heic",
			"image/heif", "heif"
	);
	private static final int THUMBNAIL_MAX_DIMENSION = 480;
	private static final int MAX_IMAGE_DIMENSION = 8_192;
	private static final long MAX_IMAGE_PIXELS = 16_000_000L;
	private static final float THUMBNAIL_JPEG_QUALITY = 0.78f;
	private static final float ORIENTED_JPEG_QUALITY = 0.92f;
	private static final float THUMBNAIL_WEBP_QUALITY = 74.0f;
	static final String THUMBNAIL_DIRECTORY = "_thumbs";
	private static volatile Boolean cwebpAvailable;

	private final Path baseDir;
	private final String publicPrefix;
	private final Executor thumbnailExecutor;

	@Autowired
	public StorageService(
			@Value("${neostride.upload.base-dir:./uploads}") String baseDir,
			@Value("${neostride.upload.public-prefix:/uploads}") String publicPrefix,
			@Qualifier("imageThumbnailExecutor") Executor thumbnailExecutor
	) {
		this(Path.of(baseDir), publicPrefix, thumbnailExecutor);
	}

	public StorageService(Path baseDir, String publicPrefix) {
		this(baseDir, publicPrefix, Runnable::run);
	}

	StorageService(Path baseDir, String publicPrefix, Executor thumbnailExecutor) {
		this.baseDir = baseDir.toAbsolutePath().normalize();
		this.publicPrefix = normalizePublicPrefix(publicPrefix);
		this.thumbnailExecutor = thumbnailExecutor == null ? Runnable::run : thumbnailExecutor;
	}

	public String storeImage(MultipartFile file, String directory) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
		}
		String declaredContentType = normalize(file.getContentType());
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException exception) {
			throw new IllegalStateException("업로드 파일을 읽을 수 없습니다.", exception);
		}
		if (!ALLOWED_CONTENT_TYPES.contains(declaredContentType) && !declaredContentType.isBlank() && !"application/octet-stream".equals(declaredContentType)) {
			throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다. jpg, jpeg, png, webp, heic, heif만 업로드할 수 있습니다.");
		}
		String actualContentType = detectContentType(bytes);
		if (actualContentType == null) {
			throw new IllegalArgumentException("이미지 파일 내용이 지원하는 이미지 형식과 일치하지 않습니다.");
		}
		BufferedImage raster = validateDecodableRaster(bytes, actualContentType);
		byte[] storedBytes = bytes;
		if ("image/jpeg".equals(actualContentType) && raster != null) {
			BufferedImage oriented = applyExifOrientation(raster, readExifOrientation(bytes));
			if (oriented != raster) {
				raster = oriented;
				try {
					storedBytes = jpegBytes(oriented, ORIENTED_JPEG_QUALITY);
				} catch (IOException exception) {
					throw new IllegalStateException("이미지 회전 보정에 실패했습니다.", exception);
				}
			}
		}

		String safeDirectory = safeDirectory(directory);
		String storedExtension = EXTENSION_BY_CONTENT_TYPE.get(actualContentType);
		String filename = UUID.randomUUID() + "." + storedExtension;
		Path targetDirectory = baseDir.resolve(safeDirectory).normalize();
		if (!targetDirectory.startsWith(baseDir)) {
			throw new IllegalArgumentException("업로드 경로가 올바르지 않습니다.");
		}
		Path target = targetDirectory.resolve(filename);
		try {
			Files.createDirectories(targetDirectory);
			Files.write(target, storedBytes);
		} catch (IOException exception) {
			throw new IllegalStateException("이미지 파일 저장에 실패했습니다.", exception);
		}
		scheduleThumbnailIfPossible(target, actualContentType);
		return publicPrefix + "/" + safeDirectory + "/" + filename;
	}

	public void deleteStoredImage(String publicUrl) {
		if (publicUrl == null || publicUrl.isBlank()) {
			return;
		}
		String normalizedUrl = publicUrl.trim();
		String expectedPrefix = publicPrefix + "/";
		if (!normalizedUrl.startsWith(expectedPrefix)) {
			return;
		}
		Path target = baseDir.resolve(normalizedUrl.substring(expectedPrefix.length())).normalize();
		if (!target.startsWith(baseDir)) {
			return;
		}
		try {
			Files.deleteIfExists(target);
			deleteThumbnailIfExists(target);
		} catch (IOException exception) {
			log.warn("Failed to delete stored image {}", publicUrl, exception);
		}
	}

	private static void deleteThumbnailIfExists(Path target) throws IOException {
		Path filename = target.getFileName();
		Path parent = target.getParent();
		if (filename == null || parent == null) {
			return;
		}
		Path thumbnailDirectory = parent.resolve(THUMBNAIL_DIRECTORY);
		String filenameText = filename.toString();
		Files.deleteIfExists(thumbnailDirectory.resolve(thumbnailFilename(filenameText)));
		Files.deleteIfExists(thumbnailDirectory.resolve(thumbnailWebpFilename(filenameText)));
	}

	private static BufferedImage validateDecodableRaster(byte[] bytes, String contentType) {
		if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
			return null;
		}
		validateImageDimensions(bytes, contentType);
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
			BufferedImage image = ImageIO.read(input);
			if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
				throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.");
			}
			validatePixelCount(image.getWidth(), image.getHeight());
			return image;
		} catch (IOException exception) {
			throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.", exception);
		}
	}

	private static void validateImageDimensions(byte[] bytes, String contentType) {
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
				ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
			validateImageDimensions(imageInput, contentType);
		} catch (IOException exception) {
			throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.", exception);
		}
	}

	private static void validateImageDimensions(Path source) {
		try (ImageInputStream imageInput = ImageIO.createImageInputStream(source.toFile())) {
			validateImageDimensions(imageInput, null);
		} catch (IOException exception) {
			throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.", exception);
		}
	}

	private static void validateImageDimensions(ImageInputStream imageInput, String contentType) throws IOException {
		if (imageInput == null) {
			throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.");
		}
		Iterator<ImageReader> readers = contentType == null
				? ImageIO.getImageReaders(imageInput)
				: ImageIO.getImageReadersByMIMEType(contentType);
		if (!readers.hasNext()) {
			throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.");
		}
		ImageReader reader = readers.next();
		try {
			reader.setInput(imageInput, true, true);
			validatePixelCount(reader.getWidth(0), reader.getHeight(0));
		} catch (IndexOutOfBoundsException exception) {
			throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.", exception);
		} finally {
			reader.dispose();
		}
	}

	private static void validatePixelCount(int width, int height) {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.");
		}
		long pixels = (long) width * height;
		if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION || pixels > MAX_IMAGE_PIXELS) {
			throw new IllegalArgumentException("이미지 크기가 너무 큽니다.");
		}
	}

	private static BufferedImage applyExifOrientation(BufferedImage source, int orientation) {
		if (orientation <= 1 || orientation > 8) {
			return source;
		}
		int width = source.getWidth();
		int height = source.getHeight();
		int targetWidth = orientation >= 5 ? height : width;
		int targetHeight = orientation >= 5 ? width : height;
		BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int targetX;
				int targetY;
				switch (orientation) {
					case 2 -> {
						targetX = width - 1 - x;
						targetY = y;
					}
					case 3 -> {
						targetX = width - 1 - x;
						targetY = height - 1 - y;
					}
					case 4 -> {
						targetX = x;
						targetY = height - 1 - y;
					}
					case 5 -> {
						targetX = y;
						targetY = x;
					}
					case 6 -> {
						targetX = height - 1 - y;
						targetY = x;
					}
					case 7 -> {
						targetX = height - 1 - y;
						targetY = width - 1 - x;
					}
					case 8 -> {
						targetX = y;
						targetY = width - 1 - x;
					}
					default -> {
						targetX = x;
						targetY = y;
					}
				}
				target.setRGB(targetX, targetY, source.getRGB(x, y));
			}
		}
		return target;
	}

	private static int readExifOrientation(byte[] bytes) {
		if (bytes.length < 4 || (bytes[0] & 0xff) != 0xff || (bytes[1] & 0xff) != 0xd8) {
			return 1;
		}
		int offset = 2;
		while (offset + 4 <= bytes.length) {
			if ((bytes[offset] & 0xff) != 0xff) {
				return 1;
			}
			while (offset < bytes.length && (bytes[offset] & 0xff) == 0xff) {
				offset++;
			}
			if (offset >= bytes.length) {
				return 1;
			}
			int marker = bytes[offset++] & 0xff;
			if (marker == 0xda || marker == 0xd9) {
				return 1;
			}
			if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
				continue;
			}
			if (offset + 2 > bytes.length) {
				return 1;
			}
			int segmentLength = readUnsignedShortBigEndian(bytes, offset);
			if (segmentLength < 2 || offset + segmentLength > bytes.length) {
				return 1;
			}
			int segmentStart = offset + 2;
			int segmentEnd = offset + segmentLength;
			if (marker == 0xe1 && hasExifHeader(bytes, segmentStart, segmentEnd)) {
				return readTiffOrientation(bytes, segmentStart + 6, segmentEnd);
			}
			offset += segmentLength;
		}
		return 1;
	}

	private static boolean hasExifHeader(byte[] bytes, int start, int end) {
		return start + 6 <= end
				&& bytes[start] == 'E'
				&& bytes[start + 1] == 'x'
				&& bytes[start + 2] == 'i'
				&& bytes[start + 3] == 'f'
				&& bytes[start + 4] == 0
				&& bytes[start + 5] == 0;
	}

	private static int readTiffOrientation(byte[] bytes, int tiffStart, int end) {
		if (tiffStart + 8 > end) {
			return 1;
		}
		boolean littleEndian;
		if (bytes[tiffStart] == 'I' && bytes[tiffStart + 1] == 'I') {
			littleEndian = true;
		} else if (bytes[tiffStart] == 'M' && bytes[tiffStart + 1] == 'M') {
			littleEndian = false;
		} else {
			return 1;
		}
		if (readUnsignedShort(bytes, tiffStart + 2, littleEndian) != 42) {
			return 1;
		}
		long firstIfdOffset = readUnsignedInt(bytes, tiffStart + 4, littleEndian);
		if (firstIfdOffset < 0 || firstIfdOffset > Integer.MAX_VALUE) {
			return 1;
		}
		int ifdStart = tiffStart + (int) firstIfdOffset;
		if (ifdStart < tiffStart || ifdStart + 2 > end) {
			return 1;
		}
		int entryCount = readUnsignedShort(bytes, ifdStart, littleEndian);
		int entriesStart = ifdStart + 2;
		for (int i = 0; i < entryCount; i++) {
			int entryOffset = entriesStart + i * 12;
			if (entryOffset + 12 > end) {
				return 1;
			}
			int tag = readUnsignedShort(bytes, entryOffset, littleEndian);
			int type = readUnsignedShort(bytes, entryOffset + 2, littleEndian);
			long count = readUnsignedInt(bytes, entryOffset + 4, littleEndian);
			if (tag == 0x0112 && type == 3 && count == 1) {
				int value = readUnsignedShort(bytes, entryOffset + 8, littleEndian);
				return value >= 1 && value <= 8 ? value : 1;
			}
		}
		return 1;
	}

	private static int readUnsignedShortBigEndian(byte[] bytes, int offset) {
		return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
	}

	private static int readUnsignedShort(byte[] bytes, int offset, boolean littleEndian) {
		if (littleEndian) {
			return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
		}
		return readUnsignedShortBigEndian(bytes, offset);
	}

	private static long readUnsignedInt(byte[] bytes, int offset, boolean littleEndian) {
		if (littleEndian) {
			return (long) (bytes[offset] & 0xff)
					| ((long) (bytes[offset + 1] & 0xff) << 8)
					| ((long) (bytes[offset + 2] & 0xff) << 16)
					| ((long) (bytes[offset + 3] & 0xff) << 24);
		}
		return ((long) (bytes[offset] & 0xff) << 24)
				| ((long) (bytes[offset + 1] & 0xff) << 16)
				| ((long) (bytes[offset + 2] & 0xff) << 8)
				| (long) (bytes[offset + 3] & 0xff);
	}

	private void scheduleThumbnailIfPossible(Path source) {
		try {
			thumbnailExecutor.execute(() -> writeThumbnailIfPossible(source));
		} catch (RejectedExecutionException exception) {
			log.warn("Rejected image thumbnail task for {}", source.getFileName(), exception);
		}
	}

	private void scheduleThumbnailIfPossible(Path source, String contentType) {
		if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
			return;
		}
		scheduleThumbnailIfPossible(source);
	}

	private static void writeThumbnailIfPossible(Path source) {
		String filename = source.getFileName().toString();
		try {
			if (Files.notExists(source)) {
				return;
			}
			validateImageDimensions(source);
			if (Files.notExists(source)) {
				return;
			}
			BufferedImage image = ImageIO.read(source.toFile());
			if (image == null) {
				throw new IOException("Image file is not decodable");
			}
			validatePixelCount(image.getWidth(), image.getHeight());
			BufferedImage thumbnail = resize(image, THUMBNAIL_MAX_DIMENSION);
			if (Files.notExists(source)) {
				return;
			}
			Path thumbnailDirectory = source.getParent().resolve(THUMBNAIL_DIRECTORY);
			Files.createDirectories(thumbnailDirectory);
			Path jpegThumbnail = thumbnailDirectory.resolve(thumbnailFilename(filename));
			writeJpeg(thumbnail, jpegThumbnail);
			if (Files.notExists(source)) {
				deleteThumbnailIfExists(source);
				return;
			}
			writeWebpIfAvailable(jpegThumbnail, thumbnailDirectory.resolve(thumbnailWebpFilename(filename)));
			if (Files.notExists(source)) {
				deleteThumbnailIfExists(source);
			}
		} catch (IOException | RuntimeException exception) {
			log.warn("Failed to create image thumbnail for {}", filename, exception);
		}
	}

	private static String thumbnailFilename(String filename) {
		int extensionIndex = filename.lastIndexOf('.');
		String basename = extensionIndex < 0 ? filename : filename.substring(0, extensionIndex);
		return basename + "_" + THUMBNAIL_MAX_DIMENSION + ".jpg";
	}

	private static String thumbnailWebpFilename(String filename) {
		int extensionIndex = filename.lastIndexOf('.');
		String basename = extensionIndex < 0 ? filename : filename.substring(0, extensionIndex);
		return basename + "_" + THUMBNAIL_MAX_DIMENSION + ".webp";
	}

	private static BufferedImage resize(BufferedImage source, int maxDimension) {
		int width = source.getWidth();
		int height = source.getHeight();
		double scale = Math.min(1.0d, (double) maxDimension / Math.max(width, height));
		int targetWidth = Math.max(1, (int) Math.round(width * scale));
		int targetHeight = Math.max(1, (int) Math.round(height * scale));
		BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, targetWidth, targetHeight);
			graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

	private static byte[] jpegBytes(BufferedImage image, float quality) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
			writeJpeg(image, output, quality);
		}
		return bytes.toByteArray();
	}

	private static void writeJpeg(BufferedImage image, Path target) throws IOException {
		try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
			writeJpeg(image, output, THUMBNAIL_JPEG_QUALITY);
		}
	}

	private static void writeJpeg(BufferedImage image, ImageOutputStream output, float quality) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		ImageWriteParam params = writer.getDefaultWriteParam();
		if (params.canWriteCompressed()) {
			params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			params.setCompressionQuality(quality);
		}
		try {
			writer.setOutput(output);
			writer.write(null, new IIOImage(image, null, null), params);
		} finally {
			writer.dispose();
		}
	}

	private static void writeWebpIfAvailable(Path source, Path target) {
		if (!cwebpAvailable()) {
			return;
		}
		try {
			Process process = new ProcessBuilder("cwebp", "-quiet", "-q",
					String.valueOf(THUMBNAIL_WEBP_QUALITY), source.toString(), "-o", target.toString())
					.redirectErrorStream(true)
					.start();
			if (!process.waitFor(10, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				Files.deleteIfExists(target);
				return;
			}
			if (process.exitValue() != 0 || !Files.isRegularFile(target) || Files.size(target) == 0) {
				Files.deleteIfExists(target);
			}
		} catch (IOException exception) {
			cwebpAvailable = false;
			try {
				Files.deleteIfExists(target);
			} catch (IOException ignored) {
				// Best-effort cleanup only.
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private static boolean cwebpAvailable() {
		Boolean cached = cwebpAvailable;
		if (cached != null) {
			return cached;
		}
		try {
			Process process = new ProcessBuilder("cwebp", "-version")
					.redirectErrorStream(true)
					.start();
			boolean available = process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
			cwebpAvailable = available;
			return available;
		} catch (IOException exception) {
			cwebpAvailable = false;
			return false;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static String detectContentType(byte[] bytes) {
		for (String contentType : ALLOWED_CONTENT_TYPES) {
			if (matchesMagicBytes(bytes, contentType)) {
				return contentType;
			}
		}
		return null;
	}

	private static boolean matchesMagicBytes(byte[] bytes, String contentType) {
		return switch (contentType) {
			case "image/jpeg" -> bytes.length >= 3
					&& (bytes[0] & 0xff) == 0xff
					&& (bytes[1] & 0xff) == 0xd8
					&& (bytes[2] & 0xff) == 0xff;
			case "image/png" -> bytes.length >= 8
					&& (bytes[0] & 0xff) == 0x89
					&& bytes[1] == 'P'
					&& bytes[2] == 'N'
					&& bytes[3] == 'G'
					&& bytes[4] == 13
					&& bytes[5] == 10
					&& bytes[6] == 26
					&& bytes[7] == 10;
			case "image/webp" -> bytes.length >= 12
					&& bytes[0] == 'R'
					&& bytes[1] == 'I'
					&& bytes[2] == 'F'
					&& bytes[3] == 'F'
					&& bytes[8] == 'W'
					&& bytes[9] == 'E'
					&& bytes[10] == 'B'
					&& bytes[11] == 'P';
			case "image/heic" -> hasIsoBaseMediaBrand(bytes, Set.of("heic", "heix", "hevc", "hevx"));
			case "image/heif" -> hasIsoBaseMediaBrand(bytes, Set.of("mif1", "msf1"));
			default -> false;
		};
	}

	private static boolean hasIsoBaseMediaBrand(byte[] bytes, Set<String> brands) {
		if (bytes.length < 12 || bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
			return false;
		}
		if (hasBrandAt(bytes, 8, brands)) {
			return true;
		}
		for (int offset = 16; offset + 3 < bytes.length; offset += 4) {
			if (hasBrandAt(bytes, offset, brands)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasBrandAt(byte[] bytes, int offset, Set<String> brands) {
		String brand = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
		return brands.contains(brand);
	}

	private static String safeDirectory(String directory) {
		String normalized = directory == null || directory.isBlank() ? "images" : directory.trim().toLowerCase(Locale.ROOT);
		String safe = normalized.replaceAll("[^a-z0-9_-]", "");
		return safe.isBlank() ? "images" : safe;
	}

	private static String normalizePublicPrefix(String publicPrefix) {
		String prefix = publicPrefix == null || publicPrefix.isBlank() ? "/uploads" : publicPrefix.trim();
		if (!prefix.startsWith("/")) {
			prefix = "/" + prefix;
		}
		while (prefix.length() > 1 && prefix.endsWith("/")) {
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		return prefix;
	}
}
