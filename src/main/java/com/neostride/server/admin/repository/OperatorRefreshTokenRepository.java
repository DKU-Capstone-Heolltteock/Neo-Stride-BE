package com.neostride.server.admin.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperatorRefreshTokenRepository {
	private final JdbcTemplate jdbcTemplate;

	public OperatorRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void save(long operatorAccountId, String tokenId, long expiresAtEpochSeconds) {
		if (tokenId == null || tokenId.isBlank()) {
			throw new IllegalArgumentException("refresh token id is required.");
		}
		jdbcTemplate.update("""
				INSERT INTO operator_refresh_tokens (operator_account_id, token_id_hash, expires_at)
				VALUES (?, ?, FROM_UNIXTIME(?))
				""", operatorAccountId, sha256Hex(tokenId), expiresAtEpochSeconds);
	}

	public boolean revokeIfActive(long operatorAccountId, String tokenId) {
		if (tokenId == null || tokenId.isBlank()) {
			return false;
		}
		int updated = jdbcTemplate.update("""
				UPDATE operator_refresh_tokens
				SET revoked_at = NOW()
				WHERE operator_account_id = ?
				  AND token_id_hash = ?
				  AND revoked_at IS NULL
				  AND expires_at > NOW()
				""", operatorAccountId, sha256Hex(tokenId));
		return updated > 0;
	}

	public void revoke(long operatorAccountId, String tokenId) {
		if (tokenId == null || tokenId.isBlank()) {
			return;
		}
		jdbcTemplate.update("""
				UPDATE operator_refresh_tokens
				SET revoked_at = COALESCE(revoked_at, NOW())
				WHERE operator_account_id = ?
				  AND token_id_hash = ?
				  AND revoked_at IS NULL
				""", operatorAccountId, sha256Hex(tokenId));
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
