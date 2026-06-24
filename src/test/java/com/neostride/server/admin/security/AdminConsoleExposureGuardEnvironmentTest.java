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
	void uppercaseEnvironmentVariablesCanEnableProdConsoleWithTailscaleSurface() throws Exception {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("ADMIN_EXPOSURE_ENABLED", "true")
				.withProperty("ADMIN_ALLOWED_SURFACES", "tailscale")
				.withProperty("ADMIN_ALLOWED_CIDRS", "100.64.0.0/10");
		environment.setActiveProfiles("prod");
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(environment);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("GET", "/api/admin/me", "100.64.12.34"), response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void legacyConsoleEnvironmentVariablesStillEnableProdConsoleWithAllowlist() throws Exception {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("ADMIN_CONSOLE_ENABLED", "true")
				.withProperty("ADMIN_CONSOLE_ALLOWED_IP_RANGES", "203.0.113.7");
		environment.setActiveProfiles("prod");
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(environment);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("GET", "/api/admin/me", "203.0.113.7"), response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private static MockHttpServletRequest request(String method, String path, String remoteAddr) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(remoteAddr);
		return request;
	}
}
