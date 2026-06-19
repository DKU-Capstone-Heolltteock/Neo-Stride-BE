package com.neostride.server.auth.service;

import com.neostride.server.auth.api.UserAdministrationPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SuspendedAccountAccessFilter extends OncePerRequestFilter {
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenService jwtTokenService;
	private final UserAdministrationPort userAdministrationPort;

@Autowired
	public SuspendedAccountAccessFilter(JwtTokenService jwtTokenService, UserAdministrationPort userAdministrationPort) {
		this.jwtTokenService = jwtTokenService;
		this.userAdministrationPort = userAdministrationPort;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!shouldCheck(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			filterChain.doFilter(request, response);
			return;
		}
		try {
			JwtTokenService.TokenClaims claims = jwtTokenService.verify(authorization.substring(BEARER_PREFIX.length()).trim());
			if ("access".equals(claims.type())) {
				var account = userAdministrationPort.findAccount(claims.userId());
				if (account.isPresent() && "SUSPENDED".equals(account.get().status())) {
					response.setStatus(HttpStatus.FORBIDDEN.value());
					response.setContentType("application/json;charset=UTF-8");
					response.getWriter().write("{\"status\":\"error\",\"message\":\"정지된 계정입니다.\"}");
					return;
				}
			}
		} catch (IllegalArgumentException ignored) {
		}
		filterChain.doFilter(request, response);
	}

	private boolean shouldCheck(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/api/")
				&& !path.startsWith("/api/auth")
				&& !path.startsWith("/api/admin")
				&& !path.startsWith("/api/ops")
				&& !path.startsWith("/api/dev");
	}
}
