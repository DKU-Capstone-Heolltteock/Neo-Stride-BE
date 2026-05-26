package com.neostride.server.auth.service;

import com.neostride.server.auth.dto.LoginRequest;
import com.neostride.server.auth.dto.LoginResponse;
import com.neostride.server.auth.dto.SignupRequest;
import com.neostride.server.auth.dto.SignupResponse;
import com.neostride.server.auth.exception.DuplicateEmailException;
import com.neostride.server.auth.exception.InvalidCredentialsException;
import com.neostride.server.auth.repository.UserRepository;
import com.neostride.server.auth.repository.UserRow;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final String INVALID_CREDENTIALS_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

	private final UserRepository userRepository;
	private final PasswordHashService passwordHashService;
	private final JwtTokenService jwtTokenService;

	public AuthService(UserRepository userRepository, PasswordHashService passwordHashService, JwtTokenService jwtTokenService) {
		this.userRepository = userRepository;
		this.passwordHashService = passwordHashService;
		this.jwtTokenService = jwtTokenService;
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		return signup(request, null);
	}

	@Transactional
	public SignupResponse signup(SignupRequest request, String profilePhotoUrl) {
		validateSignup(request);
		String email = normalizeEmail(request.email());
		String name = request.name().trim();
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException("이미 가입된 이메일입니다.");
		}
		String hashedPassword = passwordHashService.hash(request.password());
		long userId = userRepository.insertUser(email, hashedPassword, name, normalizeOptionalUrl(profilePhotoUrl));
		return SignupResponse.success(userId, email, name);
	}

	@Transactional(readOnly = true)
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
		return LoginResponse.success(user.userId(), user.email(), user.name(), accessToken, refreshToken);
	}

	@Transactional
	public LoginResponse refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidCredentialsException("유효하지 않은 리프레시 토큰입니다.");
		}
		JwtTokenService.TokenClaims claims;
		try {
			claims = jwtTokenService.verify(refreshToken);
		} catch (IllegalArgumentException exception) {
			throw new InvalidCredentialsException("유효하지 않은 리프레시 토큰입니다.");
		}
		if (!"refresh".equals(claims.type())) {
			throw new InvalidCredentialsException("유효하지 않은 리프레시 토큰입니다.");
		}
		String accessToken = jwtTokenService.generateAccessToken(claims.userId(), claims.email(), claims.name());
		String nextRefreshToken = jwtTokenService.generateRefreshToken(claims.userId(), claims.email(), claims.name());
		return LoginResponse.success(claims.userId(), claims.email(), claims.name(), accessToken, nextRefreshToken);
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
}
