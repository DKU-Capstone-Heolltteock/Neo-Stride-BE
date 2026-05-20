package com.neostride.server.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp"
	);

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
		String contentType = normalize(file.getContentType());
		String extension = extension(file.getOriginalFilename());
		if (!ALLOWED_CONTENT_TYPES.contains(contentType) || !ALLOWED_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다. jpg, jpeg, png, webp만 업로드할 수 있습니다.");
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException exception) {
			throw new IllegalStateException("업로드 파일을 읽을 수 없습니다.", exception);
		}
		if (!matchesMagicBytes(bytes, contentType)) {
			throw new IllegalArgumentException("이미지 파일 내용이 MIME type과 일치하지 않습니다.");
		}

		String safeDirectory = safeDirectory(directory);
		String storedExtension = EXTENSION_BY_CONTENT_TYPE.getOrDefault(contentType, extension.equals("jpeg") ? "jpg" : extension);
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
		return publicPrefix + "/" + safeDirectory + "/" + filename;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static String extension(String filename) {
		if (filename == null || filename.isBlank()) {
			return "";
		}
		int dotIndex = filename.lastIndexOf('.');
		if (dotIndex < 0 || dotIndex == filename.length() - 1) {
			return "";
		}
		return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
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
			default -> false;
		};
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
