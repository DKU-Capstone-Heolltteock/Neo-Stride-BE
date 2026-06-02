package com.neostride.server.community.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

final class CommunityContentCodec {
	private static final String IMAGE_DELIMITER = "\n---NEOSTRIDE-IMAGE---\n";
	private static final String FEED_DELIMITER = "\n---NEOSTRIDE-FEED---\n";
	private static final String ROUTE_DELIMITER = "\n---NEOSTRIDE-ROUTE---\n";
	private static final String METRICS_DELIMITER = "\n---NEOSTRIDE-METRICS---\n";
	private static final String METRIC_VALUE_DELIMITER = "\n---NEOSTRIDE-METRIC---\n";
	private static final String TIP_DELIMITER = "\n---NEOSTRIDE-TIP---\n";
	private static final String ADDRESS_DELIMITER = "\n---NEOSTRIDE-ADDR---\n";

	private CommunityContentCodec() {
	}

	static String encodeFeedContent(String title, String content, String routeMapImageUrl, BigDecimal distance,
			String runningTime, String pace) {
		String encoded = safe(title) + FEED_DELIMITER + safe(content) + ROUTE_DELIMITER + safe(routeMapImageUrl);
		if (distance == null && blank(runningTime) && blank(pace)) {
			return encoded;
		}
		return encoded + METRICS_DELIMITER + safeDistance(distance) + METRIC_VALUE_DELIMITER
				+ safe(runningTime) + METRIC_VALUE_DELIMITER + safe(pace);
	}

	static DecodedFeedContent decodeFeedContent(ResultSet rs) throws SQLException {
		String title = blankToNull(rs.getString("title"));
		String content = rs.getString("body_text");
		String routeMapImageUri = blankToNull(rs.getString("route_map_image_url"));
		BigDecimal distance = rs.getBigDecimal("distance_km");
		String duration = blankToNull(rs.getString("running_time_text"));
		String pace = blankToNull(rs.getString("pace_text"));
		if (title != null || !blank(content) || routeMapImageUri != null || distance != null || duration != null
				|| pace != null) {
			return new DecodedFeedContent(title, content == null ? "" : content, routeMapImageUri,
					distance == null ? null : distance.stripTrailingZeros().toPlainString(), duration, pace);
		}
		return decodeFeedContent(rs.getString("content_text"));
	}

	static DecodedFeedContent decodeFeedContent(String raw) {
		String[] first = splitOnce(raw == null ? "" : raw, FEED_DELIMITER);
		if (first.length == 1) {
			return new DecodedFeedContent(null, first[0], null, null, null, null);
		}
		String[] second = splitOnce(first[1], ROUTE_DELIMITER);
		String routeAndMetrics = second.length > 1 ? second[1] : "";
		String[] routeSplit = splitOnce(routeAndMetrics, METRICS_DELIMITER);
		String distance = null;
		String duration = null;
		String pace = null;
		if (routeSplit.length > 1) {
			String[] distanceSplit = splitOnce(routeSplit[1], METRIC_VALUE_DELIMITER);
			distance = blankToNull(distanceSplit[0]);
			if (distanceSplit.length > 1) {
				String[] durationSplit = splitOnce(distanceSplit[1], METRIC_VALUE_DELIMITER);
				duration = blankToNull(durationSplit[0]);
				pace = durationSplit.length > 1 ? blankToNull(durationSplit[1]) : null;
			}
		}
		return new DecodedFeedContent(first[0], second[0], blankToNull(routeSplit[0]), distance, duration, pace);
	}

	static String feedDistance(ResultSet rs, DecodedFeedContent content) throws SQLException {
		if (rs.getObject("joined_running_record_id") != null) {
			return formatDistance(rs.getBigDecimal("total_distance"));
		}
		BigDecimal storedDistance = parseDistance(content.distance());
		return formatDistance(storedDistance);
	}

	static String feedDuration(ResultSet rs, DecodedFeedContent content) throws SQLException {
		if (!blank(content.duration())) {
			return content.duration();
		}
		Object duration = rs.getObject("duration");
		return duration == null ? null : formatDurationFromSeconds(((Number) duration).intValue());
	}

	static String feedPace(ResultSet rs, DecodedFeedContent content) throws SQLException {
		if (!blank(content.pace())) {
			return content.pace();
		}
		Object pace = rs.getObject("pace");
		Integer paceSeconds = paceToSeconds(pace);
		return paceSeconds == null ? null : formatPaceFromSeconds(paceSeconds);
	}

	static BigDecimal communityDistance(ResultSet rs, DecodedFeedContent content) throws SQLException {
		if (rs.getObject("joined_running_record_id") != null) {
			return nullToZero(rs.getBigDecimal("total_distance"));
		}
		BigDecimal storedDistance = parseDistance(content.distance());
		return storedDistance == null ? BigDecimal.ZERO : storedDistance;
	}

	static Integer communityDuration(ResultSet rs, DecodedFeedContent content) throws SQLException {
		if (rs.getObject("joined_running_record_id") != null) {
			return nullableInt(rs.getObject("duration"));
		}
		return parseDurationSeconds(content.duration());
	}

	static Integer communityPace(ResultSet rs, DecodedFeedContent content) throws SQLException {
		if (rs.getObject("joined_running_record_id") != null) {
			return paceToSeconds(rs.getObject("pace"));
		}
		return parsePaceSeconds(content.pace());
	}

	static String encodeTipContent(String title, String content, String routeMapImageUrl, String courseAddress) {
		return (title == null ? "" : title)
				+ TIP_DELIMITER + (content == null ? "" : content)
				+ ROUTE_DELIMITER + (routeMapImageUrl == null ? "" : routeMapImageUrl)
				+ ADDRESS_DELIMITER + (courseAddress == null ? "" : courseAddress);
	}

	static String[] decodeTipContent(ResultSet rs) throws SQLException {
		String title = rs.getString("title");
		String content = rs.getString("body_text");
		String routeMapImageUrl = blankToNull(rs.getString("route_map_image_url"));
		String courseAddress = blankToNull(rs.getString("course_address"));
		if (!blank(title) || !blank(content) || routeMapImageUrl != null || courseAddress != null) {
			return new String[]{title == null ? "" : title, content == null ? "" : content, routeMapImageUrl,
					courseAddress};
		}
		return decodeTipContent(rs.getString("content_text"));
	}

	static String[] decodeTipContent(String raw) {
		String[] first = (raw == null ? "" : raw).split(TIP_DELIMITER, 2);
		String title = first.length > 0 ? first[0] : "";
		String rest = first.length > 1 ? first[1] : "";
		String[] routeSplit = rest.split(ROUTE_DELIMITER, 2);
		String content = routeSplit.length > 0 ? routeSplit[0] : rest;
		String routeAndAddress = routeSplit.length > 1 ? routeSplit[1] : "";
		String[] addressSplit = routeAndAddress.split(ADDRESS_DELIMITER, 2);
		String routeMapImageUrl = addressSplit.length > 0 ? blankToNull(addressSplit[0]) : null;
		String courseAddress = addressSplit.length > 1 ? blankToNull(addressSplit[1]) : null;
		return new String[]{title, content, routeMapImageUrl, courseAddress};
	}

	static String imageUrls(ResultSet rs) throws SQLException {
		try {
			String normalized = rs.getString("image_urls");
			if (normalized != null) {
				return normalized;
			}
		} catch (SQLException ignored) {
			// Older tests and fallback queries may only expose the legacy image column.
		}
		try {
			return rs.getString("image");
		} catch (SQLException ignored) {
			return null;
		}
	}

	static List<String> normalizeImages(List<String> images) {
		if (images == null || images.isEmpty()) {
			return List.of();
		}
		return images.stream()
				.filter(value -> value != null && !value.isBlank())
				.map(String::trim)
				.toList();
	}

	static String encodeImages(List<String> images) {
		List<String> normalized = normalizeImages(images);
		return normalized.isEmpty() ? null : String.join(IMAGE_DELIMITER, normalized);
	}

	static List<String> decodeImages(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(raw.split(java.util.regex.Pattern.quote(IMAGE_DELIMITER)))
				.filter(value -> value != null && !value.isBlank())
				.map(String::trim)
				.toList();
	}

	static Integer parseDurationSeconds(String value) {
		if (blank(value)) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.isEmpty()) {
			return null;
		}
		String[] parts = normalized.split(":");
		if (parts.length > 3) {
			return null;
		}
		try {
			return switch (parts.length) {
				case 1 -> Integer.valueOf(parts[0].trim());
				case 2 -> Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
				case 3 -> Integer.parseInt(parts[0].trim()) * 3600
						+ Integer.parseInt(parts[1].trim()) * 60
						+ Integer.parseInt(parts[2].trim());
				default -> null;
			};
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	static Integer parsePaceSeconds(String value) {
		if (blank(value)) {
			return null;
		}
		String normalized = value.trim();
		String normalizedForDuration = normalized.replace("'", ":").replace("\"", "");
		String stripped = normalizedForDuration.replaceAll("(?i)/km.*", "").trim();
		if (stripped.endsWith(":")) {
			stripped = stripped.substring(0, stripped.length() - 1);
		}
		Integer fromDuration = stripped.contains(":") ? parseDurationSeconds(stripped) : null;
		if (fromDuration != null) {
			return fromDuration;
		}
		try {
			String cleaned = stripped.replaceAll("[^0-9.]+", "").trim();
			if (cleaned.isEmpty()) {
				return null;
			}
			return paceToSeconds(new BigDecimal(cleaned));
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static BigDecimal parseDistance(String value) {
		if (blank(value)) {
			return null;
		}
		String normalized = value.toLowerCase(Locale.ROOT).replace("km", "").trim();
		try {
			return new BigDecimal(normalized);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static String formatDistance(BigDecimal distance) {
		BigDecimal value = nullToZero(distance);
		return String.format(Locale.KOREA, "%.2f km", value);
	}

	private static String formatDurationFromSeconds(int valueInSeconds) {
		int normalized = Math.max(0, valueInSeconds);
		int hours = normalized / 3600;
		int minutes = normalized % 3600 / 60;
		int seconds = normalized % 60;
		return hours > 0
				? String.format(Locale.KOREA, "%d:%02d:%02d", hours, minutes, seconds)
				: String.format(Locale.KOREA, "%d:%02d", minutes, seconds);
	}

	private static Integer paceToSeconds(Object value) {
		if (value == null) {
			return null;
		}
		try {
			BigDecimal pace = value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
			if ((pace.scale() <= 0 || pace.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0)
					&& pace.intValue() > 59) {
				int seconds = pace.intValue();
				return seconds > 3600 ? null : seconds;
			}
			BigDecimal normalized = pace.stripTrailingZeros();
			BigDecimal absolute = normalized.abs();
			int minutes = absolute.intValue();
			int secondsPart = absolute.subtract(BigDecimal.valueOf(minutes))
					.movePointRight(Math.max(0, absolute.scale()))
					.intValue();
			if (absolute.scale() > 0 && absolute.scale() <= 2 && secondsPart < 60) {
				return minutes * 60 + secondsPart;
			}
			int seconds = Math.round(pace.multiply(BigDecimal.valueOf(60)).floatValue());
			return seconds > 3600 ? null : seconds;
		} catch (NumberFormatException | ArithmeticException exception) {
			return null;
		}
	}

	private static String formatPaceFromSeconds(int paceInSeconds) {
		int minutes = Math.max(0, paceInSeconds) / 60;
		int seconds = Math.max(0, paceInSeconds) % 60;
		return String.format(Locale.KOREA, "%d'%02d\"", minutes, seconds);
	}

	private static String[] splitOnce(String value, String delimiter) {
		int index = value.indexOf(delimiter);
		if (index < 0) {
			return new String[]{value};
		}
		return new String[]{value.substring(0, index), value.substring(index + delimiter.length())};
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static String safeDistance(BigDecimal value) {
		return value == null ? "" : value.stripTrailingZeros().toPlainString();
	}

	private static String blankToNull(String value) {
		return blank(value) ? null : value;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static Integer nullableInt(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}

	private static BigDecimal nullToZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}
}

record DecodedFeedContent(String title, String content, String routeMapImageUri, String distance, String duration,
		String pace) {
}
