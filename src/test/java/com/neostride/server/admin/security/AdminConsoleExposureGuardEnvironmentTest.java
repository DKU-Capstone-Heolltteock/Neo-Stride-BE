package com.neostride.server.admin.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminConsoleExposureGuardEnvironmentTest {
	@Test
	void uppercaseEnvironmentVariablesCanEnableProdConsoleWithAllowlist() throws Exception {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("ADMIN_CONSOLE_ENABLED", "true")
				.withProperty("ADMIN_CONSOLE_ALLOWED_IP_RANGES", "203.0.113.7");
		environment.setActiveProfiles("prod");
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(environment);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("GET", "/api/admin/me"), response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private static MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr("203.0.113.7");
		return request;
	}
}
