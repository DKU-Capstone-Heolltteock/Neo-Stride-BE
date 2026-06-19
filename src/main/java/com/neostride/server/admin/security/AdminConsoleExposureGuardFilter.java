package com.neostride.server.admin.security;

import com.neostride.server.platform.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AdminConsoleExposureGuardFilter extends OncePerRequestFilter {
	private final boolean enabled;
	private final boolean requireAllowlist;
	private final List<AdminConsoleAccessFilter.IpRange> allowedIpRanges;
	private final ClientIpResolver clientIpResolver;

	@Autowired
	public AdminConsoleExposureGuardFilter(Environment environment) {
		this(
				boolProperty(environment, "neostride.admin-console.enabled", "ADMIN_CONSOLE_ENABLED", !isProd(environment)),
				boolProperty(environment, "neostride.admin-console.require-allowlist", "ADMIN_CONSOLE_REQUIRE_ALLOWLIST", isProd(environment)),
				AdminConsoleAccessFilter.parseAllowedIpRanges(stringProperty(environment, "neostride.admin-console.allowed-ip-ranges", "ADMIN_CONSOLE_ALLOWED_IP_RANGES", "")),
				new ClientIpResolver(ClientIpResolver.parseTrustedProxyAddresses(stringProperty(environment,
						"neostride.admin-console.trusted-proxy-addresses",
						"ADMIN_CONSOLE_TRUSTED_PROXY_ADDRESSES",
						stringProperty(environment, "neostride.rate-limit.trusted-proxy-addresses", "RATE_LIMIT_TRUSTED_PROXY_ADDRESSES", "127.0.0.1,::1")
				)))
		);
	}

	AdminConsoleExposureGuardFilter(
			boolean enabled,
			boolean requireAllowlist,
			List<AdminConsoleAccessFilter.IpRange> allowedIpRanges,
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
		filterChain.doFilter(new SanitizedForwardedForRequest(request, clientIp), response);
	}

	private boolean isAllowed(String clientIp) {
		if (!requireAllowlist && allowedIpRanges.isEmpty()) {
			return true;
		}
		for (AdminConsoleAccessFilter.IpRange range : allowedIpRanges) {
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

	private static boolean boolProperty(Environment environment, String propertyName, String envName, boolean defaultValue) {
		String value = stringProperty(environment, propertyName, envName, null);
		return value == null ? defaultValue : Boolean.parseBoolean(value);
	}

	private static String stringProperty(Environment environment, String propertyName, String envName, String defaultValue) {
		String value = environment.getProperty(propertyName);
		if (value != null && !value.isBlank()) {
			return value.trim();
		}
		value = environment.getProperty(envName);
		if (value != null && !value.isBlank()) {
			return value.trim();
		}
		return defaultValue;
	}

	private static boolean isProd(Environment environment) {
		return Arrays.asList(environment.getActiveProfiles()).contains("prod");
	}

	private static final class SanitizedForwardedForRequest extends HttpServletRequestWrapper {
		private static final String FORWARDED_FOR = "X-Forwarded-For";
		private final String clientIp;

		private SanitizedForwardedForRequest(HttpServletRequest request, String clientIp) {
			super(request);
			this.clientIp = clientIp;
		}

		@Override
		public String getHeader(String name) {
			if (FORWARDED_FOR.equalsIgnoreCase(name)) {
				return clientIp;
			}
			return super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			if (FORWARDED_FOR.equalsIgnoreCase(name)) {
				return Collections.enumeration(List.of(clientIp));
			}
			return super.getHeaders(name);
		}
	}
}
