package com.neostride.server.storage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
	private static final float THUMBNAIL_JPEG_QUALITY = 0.78f;
	private static final float THUMBNAIL_WEBP_QUALITY = 74.0f;
	static final String THUMBNAIL_DIRECTORY = "_thumbs";
	private static volatile Boolean cwebpAvailable;

	private final Path baseDir;
	private final String publicPrefix;

	@Autowired
	public StorageService(
			@Value("${neostride.upload.base-dir:./uploads}") String baseDir,
			@Value("${neostride.upload.public-prefix:/uploads}") String publicPrefix
	) {
		this(Path.of(baseDir), publicPrefix);
	}

	public StorageService(Path baseDir, String publicPrefix) {
		this.baseDir = baseDir.toAbsolutePath().normalize();
		this.publicPrefix = normalizePublicPrefix(publicPrefix);
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
			Files.write(target, bytes);
		} catch (IOException exception) {
			throw new IllegalStateException("이미지 파일 저장에 실패했습니다.", exception);
		}
		writeThumbnailIfPossible(raster, targetDirectory, filename);
		return publicPrefix + "/" + safeDirectory + "/" + filename;
	}

	private static BufferedImage validateDecodableRaster(byte[] bytes, String contentType) {
		if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
			return null;
		}
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
			BufferedImage image = ImageIO.read(input);
			if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
				throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.");
			}
			return image;
		} catch (IOException exception) {
			throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.", exception);
		}
	}

	private static void writeThumbnailIfPossible(BufferedImage source, Path targetDirectory, String filename) {
		if (source == null) {
			return;
		}
		try {
			Path thumbnailDirectory = targetDirectory.resolve(THUMBNAIL_DIRECTORY);
			Files.createDirectories(thumbnailDirectory);
			Path jpegThumbnail = thumbnailDirectory.resolve(thumbnailFilename(filename));
			writeJpeg(resize(source, THUMBNAIL_MAX_DIMENSION), jpegThumbnail);
			writeWebpIfAvailable(jpegThumbnail, thumbnailDirectory.resolve(thumbnailWebpFilename(filename)));
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

	private static void writeJpeg(BufferedImage image, Path target) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		ImageWriteParam params = writer.getDefaultWriteParam();
		if (params.canWriteCompressed()) {
			params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			params.setCompressionQuality(THUMBNAIL_JPEG_QUALITY);
		}
		try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
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
