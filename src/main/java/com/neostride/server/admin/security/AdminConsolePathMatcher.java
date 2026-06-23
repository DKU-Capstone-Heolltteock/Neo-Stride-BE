package com.neostride.server.admin.security;

final class AdminConsolePathMatcher {
	private static final String ADMIN_PREFIX = "/api/admin";
	private static final String OPS_PREFIX = "/api/ops";
	private static final String DEV_PREFIX = "/api/dev";

	private AdminConsolePathMatcher() {
	}

	static boolean isConsolePath(String path) {
		String normalized = stripMatrixParameters(path);
		return hasPrefix(normalized, ADMIN_PREFIX) || hasPrefix(normalized, OPS_PREFIX) || hasPrefix(normalized, DEV_PREFIX);
	}

	private static boolean hasPrefix(String path, String prefix) {
		if (path == null) {
			return false;
		}
		if (path.equals(prefix)) {
			return true;
		}
		if (!path.startsWith(prefix)) {
			return false;
		}
		if (path.length() == prefix.length()) {
			return true;
		}
		char boundary = path.charAt(prefix.length());
		return boundary == '/' || boundary == ';';
	}

	static String stripMatrixParameters(String path) {
		if (path == null || path.indexOf(';') < 0) {
			return path;
		}
		StringBuilder normalized = new StringBuilder(path.length());
		int index = 0;
		while (index < path.length()) {
			char current = path.charAt(index);
			if (current == ';') {
				while (index < path.length() && path.charAt(index) != '/') {
					index++;
				}
				continue;
			}
			normalized.append(current);
			index++;
		}
		return normalized.toString();
	}
}
