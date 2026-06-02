package com.neostride.server.community.controller;

import com.neostride.server.storage.StorageService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class CommunityMultipartSupport {
	private final StorageService storageService;

	public CommunityMultipartSupport(StorageService storageService) {
		this.storageService = storageService;
	}

	String storeProfileImage(MultipartFile file) {
		return storageService.storeImage(file, "profile");
	}

	List<String> storedImageUris(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) return List.of();
		if (files.size() > 3) {
			throw new IllegalArgumentException("피드/팁 이미지는 최대 3장까지 업로드할 수 있습니다.");
		}
		List<String> stored = new ArrayList<>();
		for (MultipartFile file : files) {
			if (file != null && !file.isEmpty()) {
				stored.add(storageService.storeImage(file, "community"));
			}
		}
		return stored;
	}

	String storedRouteUri(MultipartFile file) {
		if (file == null) return null;
		return storageService.storeImage(file, "routes");
	}

	static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) return value.trim();
		}
		return null;
	}

	static Long firstNonNull(Long... values) {
		for (Long value : values) {
			if (value != null) return value;
		}
		return null;
	}

	static String first(Map<String, String> fields, String... names) {
		for (String name : names) {
			String value = fields.get(name);
			if (value != null && !value.isBlank()) return value.trim();
		}
		return null;
	}

	static boolean bool(String value) {
		return Boolean.parseBoolean(value != null ? value.trim() : null);
	}

	static boolean bool(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) return Boolean.parseBoolean(value.trim());
		}
		return false;
	}

	static int parseInt(Map<String, String> fields, String... names) {
		String value = first(fields, names);
		return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
	}

	static Long firstLong(Map<String, String> fields, String... names) {
		for (String name : names) {
			String value = fields.get(name);
			if (value != null && !value.isBlank()) return Long.parseLong(value.trim());
		}
		return null;
	}

	static BigDecimal decimal(String value) {
		return value == null || value.isBlank() ? null : new BigDecimal(value);
	}

	static List<Long> parseLongList(String value) {
		if (value == null || value.isBlank()) return List.of();
		String normalized = value.trim();
		if (normalized.startsWith("[") && normalized.endsWith("]")) {
			normalized = normalized.substring(1, normalized.length() - 1);
		}
		List<Long> ids = new ArrayList<>();
		for (String part : normalized.split(",")) {
			String token = part.trim();
			if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
				token = token.substring(1, token.length() - 1).trim();
			}
			if (!token.isBlank()) ids.add(Long.parseLong(token));
		}
		return ids;
	}

	static String normalizedNonBlank(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim();
	}
}
