package com.neostride.server.auth.service;

import com.neostride.server.auth.exception.AuthenticationRequiredException;
import com.neostride.server.auth.exception.ForbiddenException;
import com.neostride.server.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserService {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenService jwtTokenService;
	private final UserRepository userRepository;

	@Autowired
	public AuthenticatedUserService(JwtTokenService jwtTokenService, UserRepository userRepository) {
		this.jwtTokenService = jwtTokenService;
		this.userRepository = userRepository;
	}

	AuthenticatedUserService(JwtTokenService jwtTokenService) {
		this(jwtTokenService, null);
	}

	public long requireUserId(String authorizationHeader) {
		String token = extractBearerToken(authorizationHeader);
		JwtTokenService.TokenClaims claims;
		try {
			claims = jwtTokenService.verify(token);
		} catch (IllegalArgumentException exception) {
			throw new AuthenticationRequiredException("유효한 access token이 필요합니다.");
		}
		if (!"access".equals(claims.type())) {
			throw new AuthenticationRequiredException("access token이 필요합니다.");
		}
		if (userRepository != null && !userRepository.existsActiveById(claims.userId())) {
			throw new AuthenticationRequiredException("유효한 access token이 필요합니다.");
		}
		return claims.userId();
	}

	public void requireSameUser(long authenticatedUserId, Long requestedUserId, String fieldName) {
		if (requestedUserId == null || requestedUserId <= 0) {
			throw new IllegalArgumentException(fieldName + "는 1 이상의 값이어야 합니다.");
		}
		if (requestedUserId != authenticatedUserId) {
			throw new ForbiddenException(fieldName + "가 인증된 사용자와 일치하지 않습니다.");
		}
	}

	public void requireSameUserIfPresent(long authenticatedUserId, Long requestedUserId, String fieldName) {
		if (requestedUserId == null) {
			return;
		}
		requireSameUser(authenticatedUserId, requestedUserId, fieldName);
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
