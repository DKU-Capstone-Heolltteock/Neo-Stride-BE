package com.neostride.server.auth.service;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PasswordHashService {

	private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
	private static final String PREFIX = "pbkdf2_sha256";
	private static final int ITERATIONS = 120_000;
	private static final int SALT_BYTES = 16;
	private static final int KEY_BITS = 256;

	private final SecureRandom secureRandom = new SecureRandom();

	public String hash(String plainPassword) {
		if (plainPassword == null || plainPassword.isBlank()) {
			throw new IllegalArgumentException("password는 필수입니다.");
		}
		byte[] salt = new byte[SALT_BYTES];
		secureRandom.nextBytes(salt);
		byte[] hash = pbkdf2(plainPassword.toCharArray(), salt, ITERATIONS, KEY_BITS);
		return String.join("$",
				PREFIX,
				String.valueOf(ITERATIONS),
				Base64.getEncoder().encodeToString(salt),
				Base64.getEncoder().encodeToString(hash));
	}

	public boolean matches(String plainPassword, String encodedPassword) {
		if (plainPassword == null || encodedPassword == null) {
			return false;
		}
		String[] parts = encodedPassword.split("\\$");
		if (parts.length != 4 || !PREFIX.equals(parts[0])) {
			return false;
		}
		int iterations;
		byte[] salt;
		byte[] expectedHash;
		try {
			iterations = Integer.parseInt(parts[1]);
			salt = Base64.getDecoder().decode(parts[2]);
			expectedHash = Base64.getDecoder().decode(parts[3]);
		} catch (IllegalArgumentException exception) {
			return false;
		}
		byte[] actualHash = pbkdf2(plainPassword.toCharArray(), salt, iterations, expectedHash.length * 8);
		return constantTimeEquals(expectedHash, actualHash);
	}

	private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
		try {
			KeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
			return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
			throw new IllegalStateException("비밀번호 해시를 생성할 수 없습니다.", exception);
		}
	}

	private boolean constantTimeEquals(byte[] left, byte[] right) {
		if (left.length != right.length) {
			return false;
		}
		int diff = 0;
		for (int i = 0; i < left.length; i++) {
			diff |= left[i] ^ right[i];
		}
		return diff == 0;
	}
}
