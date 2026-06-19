package com.neostride.server.admin.service;

import com.neostride.server.admin.dto.OperatorAuthResponse;
import com.neostride.server.admin.dto.OperatorLoginRequest;
import com.neostride.server.admin.repository.OperatorRefreshTokenRepository;
import com.neostride.server.admin.repository.OperatorRepository;
import com.neostride.server.admin.security.OperatorPrincipal;
import com.neostride.server.admin.security.OperatorTokenService;
import com.neostride.server.audit.service.AuditContext;
import com.neostride.server.audit.service.AuditLogService;
import com.neostride.server.auth.exception.InvalidCredentialsException;
import com.neostride.server.auth.service.PasswordHashService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorSessionService {
	private static final String INVALID_CREDENTIALS_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";
	private static final String INVALID_REFRESH_TOKEN_MESSAGE = "유효하지 않은 관리자 리프레시 토큰입니다.";

	private final OperatorRepository operatorRepository;
	private final PasswordHashService passwordHashService;
	private final OperatorTokenService tokenService;
	private final OperatorRefreshTokenRepository refreshTokenRepository;
	private final AuditLogService auditLogService;

	public OperatorSessionService(
			OperatorRepository operatorRepository,
			PasswordHashService passwordHashService,
			OperatorTokenService tokenService,
			OperatorRefreshTokenRepository refreshTokenRepository,
			AuditLogService auditLogService
	) {
		this.operatorRepository = operatorRepository;
		this.passwordHashService = passwordHashService;
		this.tokenService = tokenService;
		this.refreshTokenRepository = refreshTokenRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public OperatorAuthResponse login(OperatorLoginRequest request, AuditContext context) {
		if (request == null || request.email() == null || request.email().isBlank() || request.password() == null || request.password().isBlank()) {
			throw new IllegalArgumentException("email과 password는 필수입니다.");
		}
		String email = request.email().trim().toLowerCase();
		var account = operatorRepository.findByEmail(email)
				.orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE));
		if (!"ACTIVE".equals(account.status()) || !passwordHashService.matches(request.password(), account.password())) {
			throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
		}
		OperatorPrincipal principal = operatorRepository.toPrincipal(account);
		String accessToken = tokenService.generateAccessToken(principal);
		String refreshToken = tokenService.generateRefreshToken(principal);
		OperatorTokenService.OperatorTokenClaims refreshClaims = tokenService.verify(refreshToken);
		refreshTokenRepository.save(principal.operatorAccountId(), refreshClaims.tokenId(), refreshClaims.expiresAt());
		operatorRepository.markLogin(principal.operatorAccountId());
		auditLogService.record(principal.operatorAccountId(), "operator.login", "operator_account",
				String.valueOf(principal.operatorAccountId()), null, null, "login success", context);
		return OperatorAuthResponse.success(
				principal.operatorAccountId(),
				principal.email(),
				principal.name(),
				principal.role(),
				principal.permissions(),
				accessToken,
				refreshToken
		);
	}

	@Transactional
	public OperatorAuthResponse refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
		OperatorTokenService.OperatorTokenClaims claims;
		try {
			claims = tokenService.verify(refreshToken);
		} catch (IllegalArgumentException exception) {
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
		if (!"operator_refresh".equals(claims.type()) || claims.tokenId() == null || claims.tokenId().isBlank()) {
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
		if (!refreshTokenRepository.revokeIfActive(claims.operatorAccountId(), claims.tokenId())) {
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
		OperatorPrincipal principal = operatorRepository.findPrincipal(claims.operatorAccountId())
				.orElseThrow(() -> new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE));
		String accessToken = tokenService.generateAccessToken(principal);
		String nextRefreshToken = tokenService.generateRefreshToken(principal);
		OperatorTokenService.OperatorTokenClaims nextRefreshClaims = tokenService.verify(nextRefreshToken);
		refreshTokenRepository.save(principal.operatorAccountId(), nextRefreshClaims.tokenId(), nextRefreshClaims.expiresAt());
		return OperatorAuthResponse.success(
				principal.operatorAccountId(),
				principal.email(),
				principal.name(),
				principal.role(),
				principal.permissions(),
				accessToken,
				nextRefreshToken
		);
	}

	@Transactional
	public OperatorAuthResponse logout(OperatorPrincipal principal, String refreshToken, AuditContext context) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			try {
				OperatorTokenService.OperatorTokenClaims claims = tokenService.verify(refreshToken);
				if ("operator_refresh".equals(claims.type()) && claims.operatorAccountId() == principal.operatorAccountId()) {
					refreshTokenRepository.revoke(principal.operatorAccountId(), claims.tokenId());
				}
			} catch (IllegalArgumentException ignored) {
			}
		}
		auditLogService.record(principal.operatorAccountId(), "operator.logout", "operator_account",
				String.valueOf(principal.operatorAccountId()), null, null, "logout", context);
		return OperatorAuthResponse.ok("로그아웃되었습니다.");
	}

	public OperatorAuthResponse me(OperatorPrincipal principal) {
		return OperatorAuthResponse.me(
				principal.operatorAccountId(),
				principal.email(),
				principal.name(),
				principal.role(),
				principal.permissions()
		);
	}
}
