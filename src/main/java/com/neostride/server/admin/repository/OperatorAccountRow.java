package com.neostride.server.admin.repository;

public record OperatorAccountRow(
		long operatorAccountId,
		String email,
		String password,
		String name,
		String role,
		String status
) {}
