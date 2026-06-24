package com.neostride.server.platform.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import org.springframework.http.HttpHeaders;

public final class CursorSupport {
	public static final String HEADER_HAS_MORE = "X-Has-More";
	public static final String HEADER_NEXT_CURSOR = "X-Next-Cursor";
	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 200;

	private CursorSupport() {
	}

	public record CursorPosition(LocalDateTime createdAt, long id) {}

	public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {}

	public static CursorPosition decode(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
			String[] parts = decoded.split("\\|", 2);
			if (parts.length != 2) {
				throw new IllegalArgumentException("cursor 값이 올바르지 않습니다.");
			}
			return new CursorPosition(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
		} catch (IllegalArgumentException | DateTimeParseException exception) {
			throw new IllegalArgumentException("cursor 값이 올바르지 않습니다.");
		}
	}

	public static String encode(LocalDateTime createdAt, long id) {
		if (createdAt == null) {
			return null;
		}
		String raw = createdAt + "|" + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	public static LocalDateTime parseDateTime(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.parse(value.trim());
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException(fieldName + " 값이 올바르지 않습니다.");
		}
	}

	public static int cappedLimit(int limit) {
		if (limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

	public static int fetchLimit(int limit) {
		return cappedLimit(limit) + 1;
	}

	public static int cappedFetchLimit(int limit) {
		if (limit <= 0) {
			return DEFAULT_LIMIT;
		}
		if (limit == MAX_LIMIT + 1) {
			return limit;
		}
		return cappedLimit(limit);
	}

	public static <T> CursorPage<T> page(List<T> rows, int limit, Function<T, LocalDateTime> createdAt, ToLongFunction<T> id) {
		int cappedLimit = cappedLimit(limit);
		boolean hasMore = rows.size() > cappedLimit;
		List<T> items = hasMore ? List.copyOf(rows.subList(0, cappedLimit)) : List.copyOf(rows);
		String nextCursor = null;
		if (hasMore && !items.isEmpty()) {
			T last = items.get(items.size() - 1);
			nextCursor = encode(createdAt.apply(last), id.applyAsLong(last));
		}
		return new CursorPage<>(items, nextCursor, hasMore);
	}

	public static HttpHeaders headers(CursorPage<?> page) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HEADER_HAS_MORE, Boolean.toString(page.hasMore()));
		if (page.nextCursor() != null) {
			headers.add(HEADER_NEXT_CURSOR, page.nextCursor());
		}
		return headers;
	}
}
