package com.neostride.server.admin.security;

import com.neostride.server.platform.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
	void tailscaleSurfaceWithoutCidrsHidesConsoleApi() throws Exception {
		AdminConsoleExposureGuardFilter filter = filter(Set.of("tailscale"), "", Set.of());
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("GET", "/api/ops/metrics/usage", "203.0.113.7"), response, chain);

		assertThat(response.getStatus()).isEqualTo(404);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void tailscaleCidrCanReachAuthChain() throws Exception {
		AdminConsoleExposureGuardFilter filter = filter(Set.of("tailscale"), "100.64.0.0/10", Set.of());
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("GET", "/api/admin/me", "100.64.12.34"), response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void untrustedForwardedForSpoofCannotReachTailscaleSurface() throws Exception {
		AdminConsoleExposureGuardFilter filter = filter(Set.of("tailscale"), "100.64.0.0/10", Set.of("127.0.0.1"));
		MockHttpServletRequest request = request("GET", "/api/admin/me", "203.0.113.7");
		request.addHeader("X-Forwarded-For", "100.64.12.34");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(404);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void untrustedForwardedForIsIgnoredAndSanitized() throws Exception {
		AdminConsoleExposureGuardFilter filter = filter(Set.of("tailscale"), "203.0.113.7", Set.of("127.0.0.1"));
		MockHttpServletRequest request = request("GET", "/api/admin/me", "203.0.113.7");
		request.addHeader("X-Forwarded-For", "198.51.100.20");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> downstreamForwardedFor = new AtomicReference<>();

		filter.doFilterInternal(request, response, captureForwardedFor(downstreamForwardedFor));

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(downstreamForwardedFor).hasValue("203.0.113.7");
	}

	@Test
	void trustedProxyForwardedForCanMatchTailscaleCidr() throws Exception {
		AdminConsoleExposureGuardFilter filter = filter(Set.of("tailscale"), "100.64.0.0/10", Set.of("127.0.0.0/8"));
		MockHttpServletRequest request = request("GET", "/api/dev/logs/errors", "127.0.0.1");
		request.addHeader("X-Forwarded-For", "100.64.12.34");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> downstreamForwardedFor = new AtomicReference<>();

		filter.doFilterInternal(request, response, captureForwardedFor(downstreamForwardedFor));

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(downstreamForwardedFor).hasValue("100.64.12.34");
	}

	@Test
	void cloudflareSurfaceRequiresTrustedProxyAllowedHostAndAccessHeader() throws Exception {
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(
				true,
				false,
				Set.of("cloudflare"),
				List.of(),
				List.of("admin-api.neostride.run"),
				true,
				new ClientIpResolver(Set.of("127.0.0.0/8"))
		);
		MockHttpServletRequest request = request("GET", "/api/admin/me", "127.0.0.1");
		request.setServerName("admin-api.neostride.run");
		request.addHeader("Cf-Access-Jwt-Assertion", "header.payload.signature");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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
	void matrixParameterConsolePathsOutsideSurfaceReturnNotFound() throws Exception {
		AdminConsoleExposureGuardFilter filter = filter(Set.of("tailscale"), "100.64.0.0/10", Set.of());
		FilterChain chain = mock(FilterChain.class);

		for (String path : List.of("/api/admin;anything/auth/login", "/api/ops;anything/metrics/usage", "/api/dev;anything/logs/errors")) {
			MockHttpServletResponse response = new MockHttpServletResponse();

			filter.doFilterInternal(request("GET", path, "203.0.113.7"), response, chain);

			assertThat(response.getStatus()).isEqualTo(404);
		}
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void nonConsolePathPassesThrough() throws Exception {
		AdminConsoleExposureGuardFilter filter = new AdminConsoleExposureGuardFilter(
				false,
				false,
				Set.of(),
				List.of(),
				List.of(),
				false,
				new ClientIpResolver(Set.of())
		);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("GET", "/api/community/feeds", "203.0.113.7"), response, chain);

		verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private static AdminConsoleExposureGuardFilter filter(Set<String> surfaces, String cidrs, Set<String> trustedProxyCidrs) {
		return new AdminConsoleExposureGuardFilter(
				true,
				false,
				surfaces,
				AdminConsoleAccessFilter.parseAllowedIpRanges(cidrs),
				List.of(),
				false,
				new ClientIpResolver(trustedProxyCidrs)
		);
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
