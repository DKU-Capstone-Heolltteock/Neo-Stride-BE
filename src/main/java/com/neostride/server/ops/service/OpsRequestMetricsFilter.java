package com.neostride.server.ops.service;

import com.neostride.server.devtools.api.ErrorEventRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OpsRequestMetricsFilter extends OncePerRequestFilter {
	private final OpsService opsService;
	private final ErrorEventRecorder errorEventRecorder;

@Autowired
	public OpsRequestMetricsFilter(OpsService opsService, ErrorEventRecorder errorEventRecorder) {
		this.opsService = opsService;
		this.errorEventRecorder = errorEventRecorder;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!request.getRequestURI().startsWith("/api/")) {
			filterChain.doFilter(request, response);
			return;
		}
		long started = System.nanoTime();
		Throwable failure = null;
		try {
			filterChain.doFilter(request, response);
		} catch (Throwable throwable) {
			failure = throwable;
			throw throwable;
		} finally {
			long durationMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
			int status = failure == null ? response.getStatus() : 500;
			opsService.recordRequest(request.getMethod(), request.getRequestURI(), status, durationMs);
			if (status >= 500 || failure != null) {
				String errorType = failure == null ? "HTTP_" + status : failure.getClass().getSimpleName();
				String message = failure == null ? null : failure.getMessage();
				errorEventRecorder.record(request.getMethod(), request.getRequestURI(), status, errorType, message, requestId(request));
			}
		}
	}

	private String requestId(HttpServletRequest request) {
		String requestId = request.getHeader("X-Request-Id");
		if (requestId == null || requestId.isBlank()) {
			return request.getHeader("X-Correlation-Id");
		}
		return requestId;
	}
}
