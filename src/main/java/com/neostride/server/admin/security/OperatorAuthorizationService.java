package com.neostride.server.admin.security;

import com.neostride.server.admin.repository.OperatorRepository;
import com.neostride.server.auth.exception.AuthenticationRequiredException;
import com.neostride.server.auth.exception.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class OperatorAuthorizationService {
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String ACCESS_TYPE = "operator_access";

	private final OperatorTokenService tokenService;
	private final OperatorRepository operatorRepository;

	public OperatorAuthorizationService(OperatorTokenService tokenService, OperatorRepository operatorRepository) {
		this.tokenService = tokenService;
		this.operatorRepository = operatorRepository;
	}

	public OperatorPrincipal requireAuthenticated(String authorizationHeader) {
		OperatorTokenService.OperatorTokenClaims claims = verify(authorizationHeader);
		if (!ACCESS_TYPE.equals(claims.type())) {
			throw new AuthenticationRequiredException("관리자 access token이 필요합니다.");
		}
		return operatorRepository.findPrincipal(claims.operatorAccountId())
				.orElseThrow(() -> new AuthenticationRequiredException("유효한 관리자 access token이 필요합니다."));
	}

	public OperatorPrincipal requirePermission(String authorizationHeader, String permission) {
		OperatorPrincipal principal = requireAuthenticated(authorizationHeader);
		if (!principal.hasPermission(permission)) {
			throw new ForbiddenException("관리자 권한이 부족합니다: " + permission);
		}
		return principal;
	}

	public OperatorPrincipal requireAnyPermission(String authorizationHeader, String... permissions) {
		OperatorPrincipal principal = requireAuthenticated(authorizationHeader);
		for (String permission : permissions) {
			if (principal.hasPermission(permission)) {
				return principal;
			}
		}
		throw new ForbiddenException("관리자 권한이 부족합니다.");
	}

	private OperatorTokenService.OperatorTokenClaims verify(String authorizationHeader) {
		String token = extractBearerToken(authorizationHeader);
		try {
			return tokenService.verify(token);
		} catch (IllegalArgumentException exception) {
			throw new AuthenticationRequiredException("유효한 관리자 access token이 필요합니다.");
		}
	}

	private String extractBearerToken(String authorizationHeader) {
		if (authorizationHeader == null || authorizationHeader.isBlank()) {
			throw new AuthenticationRequiredException("Authorization Bearer token이 필요합니다.");
		}
		if (!authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			throw new AuthenticationRequiredException("Authorization Bearer token이 필요합니다.");
		}
		String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
		if (token.isBlank()) {
			throw new AuthenticationRequiredException("Authorization Bearer token이 필요합니다.");
		}
		return token;
	}
}
