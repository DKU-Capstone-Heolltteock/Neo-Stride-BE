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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
	private static final String CLOUDFLARE_ACCESS_JWT = "Cf-Access-Jwt-Assertion";
	private static final String SURFACE_CLOUDFLARE = "cloudflare";
	private static final String SURFACE_TAILSCALE = "tailscale";

	private final boolean enabled;
	private final boolean allowLocalDevelopment;
	private final Set<String> allowedSurfaces;
	private final List<AdminConsoleAccessFilter.IpRange> allowedCidrs;
	private final List<String> allowedHosts;
	private final boolean requireCloudflareAccess;
	private final ClientIpResolver clientIpResolver;

	@Autowired
	public AdminConsoleExposureGuardFilter(Environment environment) {
		this(policyFrom(environment), resolverFrom(environment));
	}

	AdminConsoleExposureGuardFilter(
			boolean enabled,
			boolean allowLocalDevelopment,
			Set<String> allowedSurfaces,
			List<AdminConsoleAccessFilter.IpRange> allowedCidrs,
			List<String> allowedHosts,
			boolean requireCloudflareAccess,
			ClientIpResolver clientIpResolver
	) {
		this.enabled = enabled;
		this.allowLocalDevelopment = allowLocalDevelopment;
		this.allowedSurfaces = allowedSurfaces == null ? Set.of() : Set.copyOf(allowedSurfaces);
		this.allowedCidrs = allowedCidrs == null ? List.of() : List.copyOf(allowedCidrs);
		this.allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
		this.requireCloudflareAccess = requireCloudflareAccess;
		this.clientIpResolver = clientIpResolver;
	}

	private AdminConsoleExposureGuardFilter(ExposurePolicy policy, ClientIpResolver clientIpResolver) {
		this(
				policy.enabled(),
				policy.allowLocalDevelopment(),
				policy.allowedSurfaces(),
				policy.allowedCidrs(),
				policy.allowedHosts(),
				policy.requireCloudflareAccess(),
				clientIpResolver
		);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!AdminConsolePathMatcher.isConsolePath(request.getRequestURI())) {
			filterChain.doFilter(request, response);
			return;
		}
		if (!enabled) {
			writeJson(response, HttpStatus.NOT_FOUND, "존재하지 않는 API입니다.");
			return;
		}
		String clientIp = clientIpResolver.resolve(request);
		if (!isAllowedSurface(request, clientIp)) {
			writeJson(response, HttpStatus.NOT_FOUND, "존재하지 않는 API입니다.");
			return;
		}
		filterChain.doFilter(new SanitizedForwardedForRequest(request, clientIp), response);
	}

	private boolean isAllowedSurface(HttpServletRequest request, String clientIp) {
		if (allowLocalDevelopment) {
			return true;
		}
		if (allowedSurfaces.contains(SURFACE_TAILSCALE) && isAllowedClientIp(clientIp)) {
			return true;
		}
		if (allowedSurfaces.isEmpty() && isAllowedClientIp(clientIp)) {
			return true;
		}
		return allowedSurfaces.contains(SURFACE_CLOUDFLARE) && isCloudflareSurface(request);
	}

	private boolean isAllowedClientIp(String clientIp) {
		for (AdminConsoleAccessFilter.IpRange range : allowedCidrs) {
			if (range.contains(clientIp)) {
				return true;
			}
		}
		return false;
	}

	private boolean isCloudflareSurface(HttpServletRequest request) {
		if (!clientIpResolver.isTrustedProxy(request.getRemoteAddr())) {
			return false;
		}
		if (!isAllowedHost(request.getServerName())) {
			return false;
		}
		return !requireCloudflareAccess || hasHeader(request, CLOUDFLARE_ACCESS_JWT);
	}

	private boolean isAllowedHost(String serverName) {
		if (serverName == null || serverName.isBlank() || allowedHosts.isEmpty()) {
			return false;
		}
		return allowedHosts.contains(serverName.toLowerCase(Locale.ROOT));
	}

	private static boolean hasHeader(HttpServletRequest request, String name) {
		String value = request.getHeader(name);
		return value != null && !value.isBlank();
	}

	private void writeJson(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"status\":\"error\",\"message\":\"" + message + "\"}");
	}

	private static ExposurePolicy policyFrom(Environment environment) {
		boolean prod = isProd(environment);
		String legacyAllowedCidrs = stringProperty(environment, "neostride.admin-console.allowed-ip-ranges", "ADMIN_CONSOLE_ALLOWED_IP_RANGES", "");
		String allowedCidrs = stringProperty(environment, "neostride.admin-exposure.allowed-cidrs", "ADMIN_ALLOWED_CIDRS", legacyAllowedCidrs);
		Set<String> allowedSurfaces = parseLowercaseSet(stringProperty(environment, "neostride.admin-exposure.allowed-surfaces", "ADMIN_ALLOWED_SURFACES", ""));
		boolean legacyRequireAllowlist = boolProperty(environment, "neostride.admin-console.require-allowlist", "ADMIN_CONSOLE_REQUIRE_ALLOWLIST", prod);
		boolean enabled = boolProperty(
				environment,
				"neostride.admin-exposure.enabled",
				"ADMIN_EXPOSURE_ENABLED",
				boolProperty(environment, "neostride.admin-console.enabled", "ADMIN_CONSOLE_ENABLED", !prod)
		);
		boolean allowLocalDevelopment = !prod && allowedSurfaces.isEmpty() && allowedCidrs.isBlank() && !legacyRequireAllowlist;
		return new ExposurePolicy(
				enabled,
				allowLocalDevelopment,
				allowedSurfaces,
				AdminConsoleAccessFilter.parseAllowedIpRanges(allowedCidrs),
				parseLowercaseList(stringProperty(environment, "neostride.admin-exposure.allowed-hosts", "ADMIN_ALLOWED_HOSTS", "")),
				boolProperty(environment, "neostride.admin-exposure.require-cloudflare-access", "ADMIN_REQUIRE_CLOUDFLARE_ACCESS", false)
		);
	}

	private static ClientIpResolver resolverFrom(Environment environment) {
		String trustedProxyCidrs = stringProperty(
				environment,
				"neostride.admin-exposure.trusted-proxy-cidrs",
				"ADMIN_TRUSTED_PROXY_CIDRS",
				stringProperty(
						environment,
						"neostride.admin-console.trusted-proxy-addresses",
						"ADMIN_CONSOLE_TRUSTED_PROXY_ADDRESSES",
						stringProperty(environment, "neostride.rate-limit.trusted-proxy-addresses", "RATE_LIMIT_TRUSTED_PROXY_ADDRESSES", "127.0.0.1,::1")
				)
		);
		return new ClientIpResolver(ClientIpResolver.parseTrustedProxyAddresses(trustedProxyCidrs));
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

	private static Set<String> parseLowercaseSet(String raw) {
		if (raw == null || raw.isBlank()) {
			return Set.of();
		}
		Set<String> values = new LinkedHashSet<>();
		for (String part : raw.split(",")) {
			String value = part.trim().toLowerCase(Locale.ROOT);
			if (!value.isBlank()) {
				values.add(value);
			}
		}
		return Set.copyOf(values);
	}

	private static List<String> parseLowercaseList(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return Arrays.stream(raw.split(","))
				.map(String::trim)
				.map(value -> value.toLowerCase(Locale.ROOT))
				.filter(value -> !value.isBlank())
				.toList();
	}

	private static boolean isProd(Environment environment) {
		return Arrays.asList(environment.getActiveProfiles()).contains("prod");
	}

	private record ExposurePolicy(
			boolean enabled,
			boolean allowLocalDevelopment,
			Set<String> allowedSurfaces,
			List<AdminConsoleAccessFilter.IpRange> allowedCidrs,
			List<String> allowedHosts,
			boolean requireCloudflareAccess
	) {
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
