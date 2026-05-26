package com.neostride.server.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class ImageUrlResolver {
	private final String publicBaseUrl;
	private final Path uploadBaseDir;
	private final String publicPrefix;

	@Autowired
	public ImageUrlResolver(
			@Value("${neostride.upload.public-base-url:}") String publicBaseUrl,
			@Value("${neostride.upload.base-dir:./uploads}") String uploadBaseDir,
			@Value("${neostride.upload.public-prefix:/uploads}") String publicPrefix
	) {
		this(publicBaseUrl, Path.of(uploadBaseDir).toAbsolutePath().normalize(), publicPrefix);
	}

	public ImageUrlResolver(String publicBaseUrl) {
		this(publicBaseUrl, (Path) null, "/uploads");
	}

	ImageUrlResolver(String publicBaseUrl, Path uploadBaseDir, String publicPrefix) {
		this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
		this.uploadBaseDir = uploadBaseDir == null ? null : uploadBaseDir.toAbsolutePath().normalize();
		this.publicPrefix = normalizePublicPrefix(publicPrefix);
	}

	public String toPublicUrl(String value) {
		if (value == null) {
			return null;
		}
		String url = value.trim();
		if (url.isEmpty()) {
			return null;
		}
		String lower = url.toLowerCase(Locale.ROOT);
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			return url;
		}
		if (lower.startsWith("content://") || lower.startsWith("file://")) {
			return null;
		}
		if (!url.startsWith(publicPrefix + "/")) {
			return url;
		}
		if (!isReadableStoredUpload(url)) {
			return null;
		}
		String baseUrl = currentBaseUrl();
		return baseUrl == null ? url : baseUrl + url;
	}

	public List<String> toPublicUrls(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		return values.stream()
				.map(this::toPublicUrl)
				.filter(value -> value != null && !value.isBlank())
				.toList();
	}

	private boolean isReadableStoredUpload(String url) {
		if (uploadBaseDir == null) {
			return true;
		}
		String relative = url.substring(publicPrefix.length() + 1);
		Path path = uploadBaseDir.resolve(relative).normalize();
		if (!path.startsWith(uploadBaseDir) || !Files.isRegularFile(path)) {
			return false;
		}
		try {
			if (Files.size(path) <= 0) {
				return false;
			}
			String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
			if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
				return ImageIO.read(path.toFile()) != null;
			}
			return true;
		} catch (IOException exception) {
			return false;
		}
	}

	private String currentBaseUrl() {
		if (publicBaseUrl != null) {
			return publicBaseUrl;
		}
		try {
			return normalizeBaseUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
		} catch (IllegalStateException exception) {
			return null;
		}
	}

	private static String normalizeBaseUrl(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim();
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized.isBlank() ? null : normalized;
	}

	private static String normalizePublicPrefix(String value) {
		String prefix = value == null || value.isBlank() ? "/uploads" : value.trim();
		if (!prefix.startsWith("/")) {
			prefix = "/" + prefix;
		}
		while (prefix.length() > 1 && prefix.endsWith("/")) {
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		return prefix;
	}
}
