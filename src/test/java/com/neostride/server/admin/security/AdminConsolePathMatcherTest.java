package com.neostride.server.admin.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AdminConsolePathMatcherTest {
	@ParameterizedTest
	@ValueSource(strings = {
			"/api/admin",
			"/api/admin/",
			"/api/admin/auth/login",
			"/api;v=1/admin/auth/login",
			"/api/admin;anything",
			"/api/admin;anything/auth/login",
			"/api;v=1/ops/metrics/usage",
			"/api/ops;anything",
			"/api/ops;anything/metrics/usage",
			"/api;v=1/dev/logs/errors",
			"/api/dev;anything",
			"/api/dev;anything/logs/errors"
	})
	void matrixParameterConsolePathsAreProtected(String path) {
		assertThat(AdminConsolePathMatcher.isConsolePath(path)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/api/administrator",
			"/api/administer",
			"/api/admin-v2",
			"/api/opscenter",
			"/api/developer"
	})
	void similarPrefixesAreNotConsolePaths(String path) {
		assertThat(AdminConsolePathMatcher.isConsolePath(path)).isFalse();
	}
}
