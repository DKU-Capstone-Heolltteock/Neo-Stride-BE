package com.neostride.server.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClientIpResolver {
	public static final String REQUEST_ATTRIBUTE = ClientIpResolver.class.getName() + ".clientIp";

	private final Set<String> trustedProxyAddresses;
	private final List<IpRange> trustedProxyRanges;

	public ClientIpResolver(Set<String> trustedProxyAddresses) {
		this.trustedProxyAddresses = trustedProxyAddresses == null ? Set.of() : Set.copyOf(trustedProxyAddresses);
		this.trustedProxyRanges = this.trustedProxyAddresses.stream()
				.filter(value -> value.contains("/"))
				.map(IpRange::parse)
				.toList();
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

	public boolean isTrustedProxy(String remoteAddr) {
		if (remoteAddr == null || remoteAddr.isBlank()) {
			return false;
		}
		if (trustedProxyAddresses.contains(remoteAddr)) {
			return true;
		}
		for (IpRange range : trustedProxyRanges) {
			if (range.contains(remoteAddr)) {
				return true;
			}
		}
		return false;
	}

	private String resolveRaw(HttpServletRequest request) {
		String remoteAddr = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
		String forwarded = request.getHeader("X-Forwarded-For");
		if (isTrustedProxy(remoteAddr) && forwarded != null && !forwarded.isBlank()) {
			String firstForwarded = forwarded.split(",", 2)[0].trim();
			if (!firstForwarded.isBlank()) {
				return firstForwarded;
			}
		}
		return remoteAddr;
	}

	private record IpRange(byte[] address, int prefixBits, String source) {
		static IpRange parse(String raw) {
			String[] parts = raw.split("/", 2);
			try {
				byte[] address = InetAddress.getByName(parts[0].trim()).getAddress();
				int maxPrefix = address.length * 8;
				int prefixBits = parts.length == 1 ? maxPrefix : Integer.parseInt(parts[1].trim());
				if (prefixBits < 0 || prefixBits > maxPrefix) {
					throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix: " + raw);
				}
				return new IpRange(address, prefixBits, raw);
			} catch (UnknownHostException | NumberFormatException exception) {
				throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + raw, exception);
			}
		}

		boolean contains(String clientIp) {
			try {
				byte[] candidate = InetAddress.getByName(clientIp).getAddress();
				if (candidate.length != address.length) {
					return false;
				}
				int fullBytes = prefixBits / 8;
				int remainingBits = prefixBits % 8;
				for (int i = 0; i < fullBytes; i++) {
					if (candidate[i] != address[i]) {
						return false;
					}
				}
				if (remainingBits == 0) {
					return true;
				}
				int mask = 0xff << (8 - remainingBits);
				return (candidate[fullBytes] & mask) == (address[fullBytes] & mask);
			} catch (UnknownHostException exception) {
				return false;
			}
		}
	}
}
