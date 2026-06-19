package com.neostride.server.admin.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OperatorTokenService {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
	private static final int MIN_SECRET_LENGTH = 32;
	private static final String AUDIENCE = "admin";
	private static final String ACCESS_TYPE = "operator_access";
	private static final String REFRESH_TYPE = "operator_refresh";

	private final String secret;
	private final long accessTokenTtlSeconds;
	private final long refreshTokenTtlSeconds;

	public OperatorTokenService(
			@Value("${admin.jwt.secret:${jwt.secret:${JWT_SECRET:}}}") String secret,
			@Value("${admin.jwt.access-token-ttl-seconds:${ADMIN_JWT_ACCESS_TOKEN_TTL_SECONDS:3600}}") long accessTokenTtlSeconds,
			@Value("${admin.jwt.refresh-token-ttl-seconds:${ADMIN_JWT_REFRESH_TOKEN_TTL_SECONDS:1209600}}") long refreshTokenTtlSeconds
	) {
		String normalizedSecret = secret == null ? "" : secret.trim();
		if (normalizedSecret.length() < MIN_SECRET_LENGTH) {
			throw new IllegalStateException("ADMIN_JWT_SECRET or JWT_SECRET must be configured with at least 32 characters.");
		}
		if (accessTokenTtlSeconds <= 0 || refreshTokenTtlSeconds <= 0) {
			throw new IllegalStateException("Admin JWT token TTL values must be positive.");
		}
		this.secret = normalizedSecret;
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
		this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
	}

	public String generateAccessToken(OperatorPrincipal principal) {
		return generateToken(principal, ACCESS_TYPE, accessTokenTtlSeconds);
	}

	public String generateRefreshToken(OperatorPrincipal principal) {
		return generateToken(principal, REFRESH_TYPE, refreshTokenTtlSeconds);
	}

	public OperatorTokenClaims verify(String token) {
		try {
			String[] parts = token.split("\\.");
			if (parts.length != 3) {
				throw new IllegalArgumentException("JWT 형식이 올바르지 않습니다.");
			}
			String signingInput = parts[0] + "." + parts[1];
			String expectedSignature = sign(signingInput);
			if (!constantTimeEquals(expectedSignature, parts[2])) {
				throw new IllegalArgumentException("JWT 서명이 올바르지 않습니다.");
			}
			Map<String, Object> payload = OBJECT_MAPPER.readValue(URL_DECODER.decode(parts[1]), new TypeReference<>() {});
			long exp = ((Number) payload.get("exp")).longValue();
			if (Instant.now().getEpochSecond() > exp) {
				throw new IllegalArgumentException("JWT가 만료되었습니다.");
			}
			String audience = String.valueOf(payload.get("aud"));
			if (!AUDIENCE.equals(audience)) {
				throw new IllegalArgumentException("관리자 JWT audience가 올바르지 않습니다.");
			}
			return new OperatorTokenClaims(
					Long.parseLong(String.valueOf(payload.get("sub"))),
					String.valueOf(payload.get("email")),
					String.valueOf(payload.get("name")),
					String.valueOf(payload.get("role")),
					listClaim(payload.get("permissions")),
					String.valueOf(payload.get("type")),
					audience,
					payload.get("jti") == null ? null : String.valueOf(payload.get("jti")),
					exp
			);
		} catch (Exception exception) {
			throw new IllegalArgumentException("관리자 JWT를 검증할 수 없습니다.", exception);
		}
	}

	private String generateToken(OperatorPrincipal principal, String type, long ttlSeconds) {
		long now = Instant.now().getEpochSecond();
		Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
		Map<String, Object> payload = Map.of(
				"sub", String.valueOf(principal.operatorAccountId()),
				"email", principal.email(),
				"name", principal.name(),
				"role", principal.role(),
				"permissions", principal.permissions(),
				"type", type,
				"aud", AUDIENCE,
				"jti", UUID.randomUUID().toString(),
				"iat", now,
				"exp", now + ttlSeconds
		);
		String signingInput = base64UrlJson(header) + "." + base64UrlJson(payload);
		return signingInput + "." + sign(signingInput);
	}

	private List<String> listClaim(Object value) {
		if (value instanceof List<?> values) {
			return values.stream().map(String::valueOf).toList();
		}
		return List.of();
	}

	private String base64UrlJson(Map<String, Object> value) {
		try {
			return URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
		} catch (Exception exception) {
			throw new IllegalStateException("관리자 JWT payload를 생성할 수 없습니다.", exception);
		}
	}

	private String sign(String signingInput) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | InvalidKeyException exception) {
			throw new IllegalStateException("관리자 JWT 서명을 생성할 수 없습니다.", exception);
		}
	}

	private boolean constantTimeEquals(String left, String right) {
		byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
		byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
		if (leftBytes.length != rightBytes.length) {
			return false;
		}
		int diff = 0;
		for (int i = 0; i < leftBytes.length; i++) {
			diff |= leftBytes[i] ^ rightBytes[i];
		}
		return diff == 0;
	}

	public record OperatorTokenClaims(
			long operatorAccountId,
			String email,
			String name,
			String role,
			List<String> permissions,
			String type,
			String audience,
			String tokenId,
			long expiresAt
	) {
		public OperatorPrincipal principal() {
			return new OperatorPrincipal(operatorAccountId, email, name, role, permissions);
		}
	}
}
