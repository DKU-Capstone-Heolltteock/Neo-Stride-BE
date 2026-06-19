package com.neostride.server.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminRateLimitFilter extends OncePerRequestFilter {
	private static final long WINDOW_MILLIS = 60_000L;

	private final boolean enabled;
	private final int authLimit;
	private final int readLimit;
	private final int writeLimit;
	private final Clock clock;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

@Autowired
	public AdminRateLimitFilter(
			@Value("${neostride.admin.rate-limit.enabled:${RATE_LIMIT_ENABLED:true}}") boolean enabled,
			@Value("${neostride.admin.rate-limit.auth-per-minute:${ADMIN_RATE_LIMIT_AUTH_PER_MINUTE:20}}") int authLimit,
			@Value("${neostride.admin.rate-limit.read-per-minute:${ADMIN_RATE_LIMIT_READ_PER_MINUTE:600}}") int readLimit,
			@Value("${neostride.admin.rate-limit.write-per-minute:${ADMIN_RATE_LIMIT_WRITE_PER_MINUTE:120}}") int writeLimit
	) {
		this(enabled, authLimit, readLimit, writeLimit, Clock.systemUTC());
	}

	AdminRateLimitFilter(boolean enabled, int authLimit, int readLimit, int writeLimit, Clock clock) {
		this.enabled = enabled;
		this.authLimit = Math.max(1, authLimit);
		this.readLimit = Math.max(1, readLimit);
		this.writeLimit = Math.max(1, writeLimit);
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Bucket bucket = bucket(request);
		if (!enabled || bucket == null) {
			filterChain.doFilter(request, response);
			return;
		}
		String key = bucket.name() + ':' + clientKey(request);
		long now = clock.millis();
		Window window = windows.compute(key, (ignored, current) -> current == null || current.expiresAtMillis <= now
				? new Window(now + WINDOW_MILLIS)
				: current);
		int used = window.count.incrementAndGet();
		if (used > bucket.limit()) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType("application/json;charset=UTF-8");
			response.setHeader("Retry-After", String.valueOf(Math.max(1, (window.expiresAtMillis - now) / 1000)));
			response.getWriter().write("{\"status\":\"error\",\"message\":\"관리자 API 요청이 너무 많습니다.\"}");
			cleanup(now);
			return;
		}
		cleanup(now);
		filterChain.doFilter(request, response);
	}

	private Bucket bucket(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (!path.startsWith("/api/admin") && !path.startsWith("/api/ops") && !path.startsWith("/api/dev")) {
			return null;
		}
		if ("POST".equals(request.getMethod()) && path.startsWith("/api/admin/auth")) {
			return new Bucket("admin-auth", authLimit);
		}
		if ("GET".equals(request.getMethod())) {
			return new Bucket("admin-read", readLimit);
		}
		return new Bucket("admin-write", writeLimit);
	}

	private String clientKey(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",", 2)[0].trim();
		}
		return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
	}

	private void cleanup(long now) {
		if (windows.size() < 2048) {
			return;
		}
		Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getValue().expiresAtMillis <= now) {
				iterator.remove();
			}
		}
	}

	private record Bucket(String name, int limit) {}

	private static final class Window {
		private final long expiresAtMillis;
		private final AtomicInteger count = new AtomicInteger();

		private Window(long expiresAtMillis) {
			this.expiresAtMillis = expiresAtMillis;
		}
	}
}
