package com.neostride.server.auth.repository;

public record UserRow(
		long userId,
		String email,
		String name,
		String password
) {
}
