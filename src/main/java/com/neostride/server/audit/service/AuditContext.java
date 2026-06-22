package com.neostride.server.audit.service;

import jakarta.servlet.http.HttpServletRequest;

public record AuditContext(
		String requestId,
		String ipAddress,
		String userAgent
) {
	public static AuditContext from(HttpServletRequest request) {
		if (request == null) {
			return new AuditContext(null, null, null);
		}
		String requestId = firstPresent(request.getHeader("X-Request-Id"), request.getHeader("X-Correlation-Id"));
		String ipAddress = firstPresent(request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
		if (ipAddress != null && ipAddress.contains(",")) {
			ipAddress = ipAddress.split(",", 2)[0].trim();
		}
		return new AuditContext(requestId, ipAddress, request.getHeader("User-Agent"));
	}

	private static String firstPresent(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first.trim();
		}
		if (second != null && !second.isBlank()) {
			return second.trim();
		}
		return null;
	}
}
