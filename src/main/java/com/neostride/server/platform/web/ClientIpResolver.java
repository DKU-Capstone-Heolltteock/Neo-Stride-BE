package com.neostride.server.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClientIpResolver {
	public static final String REQUEST_ATTRIBUTE = ClientIpResolver.class.getName() + ".clientIp";

	private final Set<String> trustedProxyAddresses;

	public ClientIpResolver(Set<String> trustedProxyAddresses) {
		this.trustedProxyAddresses = trustedProxyAddresses == null ? Set.of() : Set.copyOf(trustedProxyAddresses);
	}

	public String resolve(HttpServletRequest request) {
		Object resolved = request.getAttribute(REQUEST_ATTRIBUTE);
		if (resolved instanceof String value && !value.isBlank()) {
			return value;
		}
		String clientIp = resolveRaw(request);
		request.setAttribute(REQUEST_ATTRIBUTE, clientIp);
		return clientIp;
	}

	public static Set<String> parseTrustedProxyAddresses(String raw) {
		if (raw == null || raw.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.collect(Collectors.toUnmodifiableSet());
	}

	private String resolveRaw(HttpServletRequest request) {
		String remoteAddr = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
		String forwarded = request.getHeader("X-Forwarded-For");
		if (trustedProxyAddresses.contains(remoteAddr) && forwarded != null && !forwarded.isBlank()) {
			String firstForwarded = forwarded.split(",", 2)[0].trim();
			if (!firstForwarded.isBlank()) {
				return firstForwarded;
			}
		}
		return remoteAddr;
	}
}
