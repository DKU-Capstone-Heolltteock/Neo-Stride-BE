package com.neostride.server.auth.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

	private final JdbcTemplate jdbcTemplate;

	public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void save(long userId, String tokenId, long expiresAtEpochSeconds) {
		if (tokenId == null || tokenId.isBlank()) {
			throw new IllegalArgumentException("refresh token id is required.");
		}
		jdbcTemplate.update("""
			INSERT INTO refresh_tokens (user_id, token_id_hash, expires_at)
			VALUES (?, ?, FROM_UNIXTIME(?))
			""", userId, sha256Hex(tokenId), expiresAtEpochSeconds);
	}

	public boolean isActive(long userId, String tokenId) {
		if (tokenId == null || tokenId.isBlank()) {
			return false;
		}
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM refresh_tokens
			WHERE user_id = ?
			  AND token_id_hash = ?
			  AND revoked_at IS NULL
			  AND expires_at > NOW()
			""", Integer.class, userId, sha256Hex(tokenId));
		return count != null && count > 0;
	}

	public boolean revokeIfActive(long userId, String tokenId) {
		if (tokenId == null || tokenId.isBlank()) {
			return false;
		}
		int updated = jdbcTemplate.update("""
			UPDATE refresh_tokens
			SET revoked_at = NOW()
			WHERE user_id = ?
			  AND token_id_hash = ?
			  AND revoked_at IS NULL
			  AND expires_at > NOW()
			""", userId, sha256Hex(tokenId));
		return updated > 0;
	}

	public boolean wasRevokedWithin(long userId, String tokenId, long graceSeconds) {
		if (tokenId == null || tokenId.isBlank() || graceSeconds <= 0) {
			return false;
		}
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM refresh_tokens
			WHERE user_id = ?
			  AND token_id_hash = ?
			  AND revoked_at IS NOT NULL
			  AND expires_at > NOW()
			  AND revoked_at >= TIMESTAMPADD(SECOND, -?, NOW())
			""", Integer.class, userId, sha256Hex(tokenId), graceSeconds);
		return count != null && count > 0;
	}

	public void revoke(long userId, String tokenId) {
		if (tokenId == null || tokenId.isBlank()) {
			return;
		}
		jdbcTemplate.update("""
			UPDATE refresh_tokens
			SET revoked_at = COALESCE(revoked_at, NOW())
			WHERE user_id = ?
			  AND token_id_hash = ?
			  AND revoked_at IS NULL
			""", userId, sha256Hex(tokenId));
	}

	private static String sha256Hex(String value) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(hash.length * 2);
			for (byte item : hash) {
				builder.append(String.format("%02x", item & 0xff));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}
}
