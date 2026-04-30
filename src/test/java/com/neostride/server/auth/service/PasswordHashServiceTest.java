package com.neostride.server.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashServiceTest {

	private final PasswordHashService passwordHashService = new PasswordHashService();

	@Test
	void hashPassword_doesNotStorePlainPasswordAndCanVerify() {
		String plainPassword = "plain-password";

		String hashedPassword = passwordHashService.hash(plainPassword);

		assertThat(hashedPassword).isNotEqualTo(plainPassword);
		assertThat(hashedPassword).startsWith("pbkdf2_sha256$");
		assertThat(passwordHashService.matches(plainPassword, hashedPassword)).isTrue();
		assertThat(passwordHashService.matches("wrong-password", hashedPassword)).isFalse();
	}
}
