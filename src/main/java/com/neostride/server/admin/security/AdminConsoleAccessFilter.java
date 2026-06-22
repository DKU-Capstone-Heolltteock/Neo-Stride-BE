package com.neostride.server.admin.security;

import com.neostride.server.platform.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminConsoleAccessFilter extends OncePerRequestFilter {
	private final boolean enabled;
	private final boolean requireAllowlist;
	private final List<IpRange> allowedIpRanges;
	private final ClientIpResolver clientIpResolver;

	@Autowired
	public AdminConsoleAccessFilter(
			@Value("${neostride.admin-console.enabled:true}") boolean enabled,
			@Value("${neostride.admin-console.require-allowlist:false}") boolean requireAllowlist,
			@Value("${neostride.admin-console.allowed-ip-ranges:}") String allowedIpRanges,
			@Value("${neostride.admin-console.trusted-proxy-addresses:${ADMIN_CONSOLE_TRUSTED_PROXY_ADDRESSES:${RATE_LIMIT_TRUSTED_PROXY_ADDRESSES:127.0.0.1,::1}}}") String trustedProxyAddresses
	) {
		this(
				enabled,
				requireAllowlist,
				parseAllowedIpRanges(allowedIpRanges),
				new ClientIpResolver(ClientIpResolver.parseTrustedProxyAddresses(trustedProxyAddresses))
		);
	}

	AdminConsoleAccessFilter(
			boolean enabled,
			boolean requireAllowlist,
			List<IpRange> allowedIpRanges,
			ClientIpResolver clientIpResolver
	) {
		this.enabled = enabled;
		this.requireAllowlist = requireAllowlist;
		this.allowedIpRanges = allowedIpRanges == null ? List.of() : List.copyOf(allowedIpRanges);
		this.clientIpResolver = clientIpResolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!isConsolePath(request.getRequestURI())) {
			filterChain.doFilter(request, response);
			return;
		}
		if (!enabled) {
			writeJson(response, HttpStatus.NOT_FOUND, "존재하지 않는 API입니다.");
			return;
		}
		String clientIp = clientIpResolver.resolve(request);
		if (!isAllowed(clientIp)) {
			writeJson(response, HttpStatus.FORBIDDEN, "관리자 콘솔 접근이 허용되지 않은 네트워크입니다.");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean isAllowed(String clientIp) {
		if (!requireAllowlist && allowedIpRanges.isEmpty()) {
			return true;
		}
		for (IpRange range : allowedIpRanges) {
			if (range.contains(clientIp)) {
				return true;
			}
		}
		return false;
	}

	private boolean isConsolePath(String path) {
		return hasPrefix(path, "/api/admin") || hasPrefix(path, "/api/ops") || hasPrefix(path, "/api/dev");
	}

	private boolean hasPrefix(String path, String prefix) {
		return path.equals(prefix) || path.startsWith(prefix + "/");
	}

	private void writeJson(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"status\":\"error\",\"message\":\"" + message + "\"}");
	}

	static List<IpRange> parseAllowedIpRanges(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.map(IpRange::parse)
				.toList();
	}

	record IpRange(byte[] address, int prefixBits, String source) {
		static IpRange parse(String raw) {
			String[] parts = raw.split("/", 2);
			try {
				byte[] address = InetAddress.getByName(parts[0].trim()).getAddress();
				int maxPrefix = address.length * 8;
				int prefixBits = parts.length == 1 ? maxPrefix : Integer.parseInt(parts[1].trim());
				if (prefixBits < 0 || prefixBits > maxPrefix) {
					throw new IllegalArgumentException("Invalid admin console IP range prefix: " + raw);
				}
				return new IpRange(address, prefixBits, raw);
			} catch (UnknownHostException | NumberFormatException exception) {
				throw new IllegalArgumentException("Invalid admin console IP range: " + raw, exception);
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
