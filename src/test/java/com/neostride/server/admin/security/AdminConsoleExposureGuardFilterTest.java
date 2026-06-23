package com.neostride.server.admin.security;

import com.neostride.server.platform.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminConsoleExposureGuardFilterTest {
	@Test
	void prodProfileDisablesConsoleApiByDefault() throws Exception {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(environment);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("GET", "/api/admin/me", "203.0.113.7"), response, chain);

		assertThat(response.getStatus()).isEqualTo(404);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void requiredAllowlistWithoutRangesRejectsConsoleApi() throws Exception {
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(
				true,
				true,
				List.of(),
				new ClientIpResolver(Set.of())
		);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("GET", "/api/ops/metrics/usage", "203.0.113.7"), response, chain);

		assertThat(response.getStatus()).isEqualTo(403);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void untrustedForwardedForIsIgnoredAndSanitized() throws Exception {
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(
				true,
				true,
				AdminConsoleAccessFilter.parseAllowedIpRanges("203.0.113.7"),
				new ClientIpResolver(Set.of("127.0.0.1"))
		);
		MockHttpServletRequest request = request("GET", "/api/admin/me", "203.0.113.7");
		request.addHeader("X-Forwarded-For", "198.51.100.20");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> downstreamForwardedFor = new AtomicReference<>();

		filter.doFilterInternal(request, response, captureForwardedFor(downstreamForwardedFor));

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(downstreamForwardedFor).hasValue("203.0.113.7");
	}

	@Test
	void trustedProxyForwardedForCanMatchAllowlist() throws Exception {
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(
				true,
				true,
				AdminConsoleAccessFilter.parseAllowedIpRanges("198.51.100.0/24"),
				new ClientIpResolver(Set.of("127.0.0.1"))
		);
		MockHttpServletRequest request = request("GET", "/api/dev/logs/errors", "127.0.0.1");
		request.addHeader("X-Forwarded-For", "198.51.100.20");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> downstreamForwardedFor = new AtomicReference<>();

		filter.doFilterInternal(request, response, captureForwardedFor(downstreamForwardedFor));

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(downstreamForwardedFor).hasValue("198.51.100.20");
	}

	@Test
	void matrixParameterAdminPathIsDisabledByProdDefault() throws Exception {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(environment);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("POST", "/api/admin;anything/auth/login", "203.0.113.7"), response, chain);

		assertThat(response.getStatus()).isEqualTo(404);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void matrixParameterConsolePathsRequireAllowlist() throws Exception {
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(
				true,
				true,
				AdminConsoleAccessFilter.parseAllowedIpRanges("198.51.100.0/24"),
				new ClientIpResolver(Set.of())
		);
		FilterChain chain = mock(FilterChain.class);

		for (String path : List.of("/api/admin;anything/auth/login", "/api/ops;anything/metrics/usage", "/api/dev;anything/logs/errors")) {
			MockHttpServletResponse response = new MockHttpServletResponse();

			filter.doFilterInternal(request("GET", path, "203.0.113.7"), response, chain);

			assertThat(response.getStatus()).isEqualTo(403);
		}
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void nonConsolePathPassesThrough() throws Exception {
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(
				false,
				true,
				List.of(),
				new ClientIpResolver(Set.of())
		);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("GET", "/api/community/feeds", "203.0.113.7"), response, chain);

		verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private static MockHttpServletRequest request(String method, String path, String remoteAddr) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(remoteAddr);
		return request;
	}

	private static FilterChain captureForwardedFor(AtomicReference<String> downstreamForwardedFor) {
		return (ServletRequest request, ServletResponse response) -> downstreamForwardedFor.set(
				((jakarta.servlet.http.HttpServletRequest) request).getHeader("X-Forwarded-For")
		);
	}
}
