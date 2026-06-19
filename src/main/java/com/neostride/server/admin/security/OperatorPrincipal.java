package com.neostride.server.admin.security;

import java.util.List;

public record OperatorPrincipal(
		long operatorAccountId,
		String email,
		String name,
		String role,
		List<String> permissions
) {
	public boolean hasPermission(String permission) {
		return permissions != null && (permissions.contains("*") || permissions.contains(permission));
	}
}
