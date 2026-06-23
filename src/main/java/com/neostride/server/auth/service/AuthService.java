package com.neostride.server.auth.service;

import com.neostride.server.auth.dto.LoginRequest;
import com.neostride.server.auth.dto.LoginResponse;
import com.neostride.server.auth.dto.SignupRequest;
import com.neostride.server.auth.dto.SignupResponse;
import com.neostride.server.auth.exception.DuplicateUserFieldException;
import com.neostride.server.auth.exception.InvalidCredentialsException;
import com.neostride.server.auth.repository.RefreshTokenRepository;
import com.neostride.server.auth.repository.UserRepository;
import com.neostride.server.auth.repository.UserRow;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final String INVALID_CREDENTIALS_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";
	private static final String INVALID_REFRESH_TOKEN_MESSAGE = "유효하지 않은 리프레시 토큰입니다.";
	private static final long DEFAULT_REFRESH_TOKEN_REPLAY_GRACE_SECONDS = 30;
	private static final int MAX_RECENT_REFRESH_RESULTS = 4096;

	private final UserRepository userRepository;
	private final PasswordHashService passwordHashService;
	private final JwtTokenService jwtTokenService;
	private final RefreshTokenRepository refreshTokenRepository;
	private final long refreshTokenReplayGraceSeconds;
	private final long refreshTokenReplayGraceMillis;
	private final ConcurrentMap<String, CompletableFuture<LoginResponse>> refreshInProgress;
	private final ConcurrentMap<String, CachedRefreshResult> recentRefreshResults;

	@Autowired
	public AuthService(
			UserRepository userRepository,
			PasswordHashService passwordHashService,
			JwtTokenService jwtTokenService,
			RefreshTokenRepository refreshTokenRepository,
			@Value("${jwt.refresh-token-replay-grace-seconds:${JWT_REFRESH_TOKEN_REPLAY_GRACE_SECONDS:30}}") long refreshTokenReplayGraceSeconds
	) {
		this.userRepository = userRepository;
		this.passwordHashService = passwordHashService;
		this.jwtTokenService = jwtTokenService;
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenReplayGraceSeconds = Math.max(0, refreshTokenReplayGraceSeconds);
		this.refreshTokenReplayGraceMillis = this.refreshTokenReplayGraceSeconds * 1000;
		this.refreshInProgress = new ConcurrentHashMap<>();
		this.recentRefreshResults = new ConcurrentHashMap<>();
	}

	public AuthService(UserRepository userRepository, PasswordHashService passwordHashService, JwtTokenService jwtTokenService, RefreshTokenRepository refreshTokenRepository) {
		this(userRepository, passwordHashService, jwtTokenService, refreshTokenRepository, DEFAULT_REFRESH_TOKEN_REPLAY_GRACE_SECONDS);
	}

	AuthService(UserRepository userRepository, PasswordHashService passwordHashService, JwtTokenService jwtTokenService) {
		this(userRepository, passwordHashService, jwtTokenService, null, DEFAULT_REFRESH_TOKEN_REPLAY_GRACE_SECONDS);
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		return signup(request, null);
	}

	@Transactional
	public SignupResponse signup(SignupRequest request, String profilePhotoUrl) {
		ValidatedSignup validatedSignup = validateSignupAvailable(request);
		String email = validatedSignup.email();
		String name = validatedSignup.name();
		String hashedPassword = passwordHashService.hash(request.password());
		long userId = userRepository.insertUser(email, hashedPassword, name, normalizeOptionalUrl(profilePhotoUrl));
		return SignupResponse.success(userId, email, name);
	}

	public void validateSignupRequestAvailable(SignupRequest request) {
		validateSignupAvailable(request);
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		validateLogin(request);
		String email = normalizeEmail(request.email());
		UserRow user = userRepository.findByEmail(email)
				.orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE));
		if (!passwordHashService.matches(request.password(), user.password())) {
			throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
		}
		String accessToken = jwtTokenService.generateAccessToken(user.userId(), user.email(), user.name());
		String refreshToken = jwtTokenService.generateRefreshToken(user.userId(), user.email(), user.name());
		persistRefreshToken(user.userId(), refreshToken);
		return LoginResponse.success(user.userId(), user.email(), user.name(), accessToken, refreshToken);
	}

	@Transactional
	public LoginResponse refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
		JwtTokenService.TokenClaims claims;
		try {
			claims = jwtTokenService.verify(refreshToken);
		} catch (IllegalArgumentException exception) {
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
		if (!"refresh".equals(claims.type())) {
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
		if (refreshTokenRepository == null) {
			String accessToken = jwtTokenService.generateAccessToken(claims.userId(), claims.email(), claims.name());
			String nextRefreshToken = jwtTokenService.generateRefreshToken(claims.userId(), claims.email(), claims.name());
			return LoginResponse.success(claims.userId(), claims.email(), claims.name(), accessToken, nextRefreshToken);
		}
		if (claims.tokenId() == null || claims.tokenId().isBlank()) {
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
		return refreshWithSingleFlight(claims);
	}

	private LoginResponse refreshWithSingleFlight(JwtTokenService.TokenClaims claims) {
		String tokenId = claims.tokenId();
		LoginResponse recent = recentRefreshResult(tokenId);
		if (recent != null) {
			return recent;
		}

		CompletableFuture<LoginResponse> inFlight = refreshInProgress.get(tokenId);
		if (inFlight != null) {
			return awaitInFlightRefresh(inFlight);
		}

		CompletableFuture<LoginResponse> newFlight = new CompletableFuture<>();
		CompletableFuture<LoginResponse> winner = refreshInProgress.putIfAbsent(tokenId, newFlight);
		if (winner != null) {
			return awaitInFlightRefresh(winner);
		}

		try {
			LoginResponse response = rotateRefreshToken(claims);
			cacheRefreshResult(tokenId, response);
			newFlight.complete(response);
			return response;
		} catch (RuntimeException exception) {
			newFlight.completeExceptionally(exception);
			throw exception;
		} finally {
			refreshInProgress.remove(tokenId, newFlight);
		}
	}

	private LoginResponse awaitInFlightRefresh(CompletableFuture<LoginResponse> flight) {
		try {
			return flight.join();
		} catch (CompletionException exception) {
			if (exception.getCause() instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
	}

	private LoginResponse rotateRefreshToken(JwtTokenService.TokenClaims claims) {
		if (!refreshTokenRepository.revokeIfActive(claims.userId(), claims.tokenId()) && !isWithinReplayGrace(claims)) {
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
		String accessToken = jwtTokenService.generateAccessToken(claims.userId(), claims.email(), claims.name());
		String nextRefreshToken = jwtTokenService.generateRefreshToken(claims.userId(), claims.email(), claims.name());
		persistRefreshToken(claims.userId(), nextRefreshToken);
		return LoginResponse.success(claims.userId(), claims.email(), claims.name(), accessToken, nextRefreshToken);
	}

	private boolean isWithinReplayGrace(JwtTokenService.TokenClaims claims) {
		return refreshTokenReplayGraceSeconds > 0
				&& refreshTokenRepository.wasRevokedWithin(claims.userId(), claims.tokenId(), refreshTokenReplayGraceSeconds);
	}

	private LoginResponse recentRefreshResult(String tokenId) {
		if (refreshTokenReplayGraceMillis <= 0) {
			return null;
		}
		long now = System.currentTimeMillis();
		CachedRefreshResult recent = recentRefreshResults.get(tokenId);
		if (recent == null) {
			return null;
		}
		if (recent.isExpired(now)) {
			recentRefreshResults.remove(tokenId, recent);
			return null;
		}
		return recent.response();
	}

	private void cacheRefreshResult(String tokenId, LoginResponse response) {
		if (refreshTokenReplayGraceMillis <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		cleanupRecentRefreshResults(now);
		recentRefreshResults.put(tokenId, new CachedRefreshResult(response, now + refreshTokenReplayGraceMillis));
	}

	private void cleanupRecentRefreshResults(long now) {
		if (recentRefreshResults.size() < MAX_RECENT_REFRESH_RESULTS) {
			return;
		}
		recentRefreshResults.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
		if (recentRefreshResults.size() >= MAX_RECENT_REFRESH_RESULTS) {
			recentRefreshResults.clear();
		}
	}

	private void persistRefreshToken(long userId, String refreshToken) {
		if (refreshTokenRepository == null) {
			return;
		}
		JwtTokenService.TokenClaims claims = jwtTokenService.verify(refreshToken);
		if (!"refresh".equals(claims.type()) || claims.tokenId() == null || claims.tokenId().isBlank()) {
			throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
		}
		refreshTokenRepository.save(userId, claims.tokenId(), claims.expiresAt());
	}

	private ValidatedSignup validateSignupAvailable(SignupRequest request) {
		validateSignup(request);
		String email = normalizeEmail(request.email());
		String name = request.name().trim();
		if (userRepository.existsByEmail(email)) {
			throw DuplicateUserFieldException.email();
		}
		if (userRepository.existsByName(name)) {
			throw DuplicateUserFieldException.name();
		}
		if (userRepository.existsByCommunityProfileName(name)) {
			throw DuplicateUserFieldException.nickname();
		}
		return new ValidatedSignup(email, name);
	}

	private void validateSignup(SignupRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		validateEmail(request.email());
		if (request.name() == null || request.name().isBlank()) {
			throw new IllegalArgumentException("name은 필수입니다.");
		}
		validatePassword(request.password());
	}

	private void validateLogin(LoginRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
		validateEmail(request.email());
		validatePassword(request.password());
	}

	private void validateEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("email은 필수입니다.");
		}
		if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
			throw new IllegalArgumentException("email 형식이 올바르지 않습니다.");
		}
	}

	private void validatePassword(String password) {
		if (password == null || password.isBlank()) {
			throw new IllegalArgumentException("password는 필수입니다.");
		}
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}

	private String normalizeOptionalUrl(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private record CachedRefreshResult(LoginResponse response, long expiresAtMillis) {
		private boolean isExpired(long nowMillis) {
			return expiresAtMillis <= nowMillis;
		}
	}

	private record ValidatedSignup(String email, String name) {
	}
}
