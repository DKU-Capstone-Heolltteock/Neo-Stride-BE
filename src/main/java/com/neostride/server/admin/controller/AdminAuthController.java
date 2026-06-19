package com.neostride.server.admin.controller;

import com.neostride.server.admin.dto.OperatorAuthResponse;
import com.neostride.server.admin.dto.OperatorLoginRequest;
import com.neostride.server.admin.dto.OperatorLogoutRequest;
import com.neostride.server.admin.dto.OperatorRefreshRequest;
import com.neostride.server.admin.security.OperatorAuthorizationService;
import com.neostride.server.admin.service.OperatorSessionService;
import com.neostride.server.audit.service.AuditContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {
	private final OperatorSessionService sessionService;
	private final OperatorAuthorizationService authorizationService;

	public AdminAuthController(OperatorSessionService sessionService, OperatorAuthorizationService authorizationService) {
		this.sessionService = sessionService;
		this.authorizationService = authorizationService;
	}

	@PostMapping("/auth/login")
	public ResponseEntity<OperatorAuthResponse> login(@RequestBody OperatorLoginRequest request, HttpServletRequest servletRequest) {
		return ResponseEntity.ok(sessionService.login(request, AuditContext.from(servletRequest)));
	}

	@PostMapping("/auth/refresh")
	public ResponseEntity<OperatorAuthResponse> refresh(@RequestBody OperatorRefreshRequest request) {
		return ResponseEntity.ok(sessionService.refresh(request.refreshToken()));
	}

	@PostMapping("/auth/logout")
	public ResponseEntity<OperatorAuthResponse> logout(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody(required = false) OperatorLogoutRequest request,
			HttpServletRequest servletRequest
	) {
		var principal = authorizationService.requireAuthenticated(authorization);
		String refreshToken = request == null ? null : request.refreshToken();
		return ResponseEntity.ok(sessionService.logout(principal, refreshToken, AuditContext.from(servletRequest)));
	}

	@GetMapping("/me")
	public ResponseEntity<OperatorAuthResponse> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
		return ResponseEntity.ok(sessionService.me(authorizationService.requireAuthenticated(authorization)));
	}
}
