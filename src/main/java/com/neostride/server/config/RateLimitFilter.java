package com.neostride.server.config;

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
public class RateLimitFilter extends OncePerRequestFilter {
	private static final long WINDOW_MILLIS = 60_000L;
	private final boolean enabled;
	private final int authLimit;
	private final int writeLimit;
	private final int readLimit;
	private final Clock clock;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	@Autowired
	public RateLimitFilter(
			@Value("${neostride.rate-limit.enabled:true}") boolean enabled,
			@Value("${neostride.rate-limit.auth-per-minute:30}") int authLimit,
			@Value("${neostride.rate-limit.write-per-minute:120}") int writeLimit,
			@Value("${neostride.rate-limit.read-per-minute:600}") int readLimit
	) {
		this(enabled, authLimit, writeLimit, readLimit, Clock.systemUTC());
	}

	RateLimitFilter(boolean enabled, int authLimit, int writeLimit, int readLimit, Clock clock) {
		this.enabled = enabled;
		this.authLimit = Math.max(1, authLimit);
		this.writeLimit = Math.max(1, writeLimit);
		this.readLimit = Math.max(1, readLimit);
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
		Window window = windows.compute(key, (ignored, current) -> {
			if (current == null || current.expiresAtMillis <= now) {
				return new Window(now + WINDOW_MILLIS);
			}
			return current;
		});
		int used = window.count.incrementAndGet();
		if (used > bucket.limit()) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType("application/json;charset=UTF-8");
			response.setHeader("Retry-After", String.valueOf(Math.max(1, (window.expiresAtMillis - now) / 1000)));
			response.getWriter().write("{\"status\":\"error\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\"}");
			cleanup(now);
			return;
		}
		cleanup(now);
		filterChain.doFilter(request, response);
	}

	private Bucket bucket(HttpServletRequest request) {
		String method = request.getMethod();
		String path = request.getRequestURI();
		if (("POST".equals(method) && path.equals("/api/auth/login"))
				|| ("POST".equals(method) && path.equals("/api/auth/refresh"))
				|| ("POST".equals(method) && path.equals("/api/auth/signup"))) {
			return new Bucket("auth", authLimit);
		}
		if (isWrite(method) && (path.startsWith("/api/community") || path.startsWith("/community")
				|| path.startsWith("/api/running") || path.startsWith("/users/me/profile-image"))) {
			return new Bucket("write", writeLimit);
		}
		if ("GET".equals(method) && (path.startsWith("/api/community/feeds") || path.startsWith("/api/community/search")
				|| path.startsWith("/api/community/tips") || path.startsWith("/community/contents"))) {
			return new Bucket("read", readLimit);
		}
		return null;
	}

	private boolean isWrite(String method) {
		return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
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
