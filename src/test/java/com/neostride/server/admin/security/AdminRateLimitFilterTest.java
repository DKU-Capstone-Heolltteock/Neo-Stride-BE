package com.neostride.server.admin.security;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AdminRateLimitFilterTest {
	@Test
	void adminAuthBucketReturnsTooManyRequestsAfterLimit() throws Exception {
		AdminRateLimitFilter filter = new AdminRateLimitFilter(
				true,
				1,
				10,
				10,
				Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneOffset.UTC)
		);
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request("POST", "/api/admin/auth/login"), new MockHttpServletResponse(), chain);
		MockHttpServletResponse secondResponse = new MockHttpServletResponse();
		filter.doFilterInternal(request("POST", "/api/admin/auth/login"), secondResponse, chain);

		verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(secondResponse.getStatus()).isEqualTo(429);
		assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
	}

	@Test
	void matrixParameterAdminAuthPathUsesAuthBucket() throws Exception {
		AdminRateLimitFilter filter = new AdminRateLimitFilter(
				true,
				1,
				10,
				10,
				Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneOffset.UTC)
		);
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request("POST", "/api;v=1/admin/auth/login"), new MockHttpServletResponse(), chain);
		MockHttpServletResponse secondResponse = new MockHttpServletResponse();
		filter.doFilterInternal(request("POST", "/api;v=2/admin/auth/login"), secondResponse, chain);

		verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(secondResponse.getStatus()).isEqualTo(429);
	}

	@Test
	void nonAdminPathPassesThrough() throws Exception {
		AdminRateLimitFilter filter = new AdminRateLimitFilter(
				true,
				1,
				1,
				1,
				Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneOffset.UTC)
		);
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request("GET", "/api/community/feeds"), new MockHttpServletResponse(), chain);
		filter.doFilterInternal(request("GET", "/api/community/feeds"), new MockHttpServletResponse(), chain);

		verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void untrustedForwardedForDoesNotResetAdminAuthBucket() throws Exception {
		AdminRateLimitFilter filter = new AdminRateLimitFilter(
				true,
				1,
				10,
				10,
				Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneOffset.UTC)
		);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletRequest first = request("POST", "/api/admin/auth/login");
		first.addHeader("X-Forwarded-For", "198.51.100.10");
		MockHttpServletRequest second = request("POST", "/api/admin/auth/login");
		second.addHeader("X-Forwarded-For", "198.51.100.11");

		filter.doFilterInternal(first, new MockHttpServletResponse(), chain);
		MockHttpServletResponse secondResponse = new MockHttpServletResponse();
		filter.doFilterInternal(second, secondResponse, chain);

		verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(secondResponse.getStatus()).isEqualTo(429);
	}

	@Test
	void trustedProxyForwardedForSeparatesAdminAuthBuckets() throws Exception {
		AdminRateLimitFilter filter = new AdminRateLimitFilter(
				true,
				1,
				10,
				10,
				Set.of("203.0.113.7"),
				Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneOffset.UTC)
		);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletRequest first = request("POST", "/api/admin/auth/login");
		first.addHeader("X-Forwarded-For", "198.51.100.10");
		MockHttpServletRequest second = request("POST", "/api/admin/auth/login");
		second.addHeader("X-Forwarded-For", "198.51.100.11");
		MockHttpServletResponse secondResponse = new MockHttpServletResponse();

		filter.doFilterInternal(first, new MockHttpServletResponse(), chain);
		filter.doFilterInternal(second, secondResponse, chain);

		verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(secondResponse.getStatus()).isEqualTo(200);
	}

	private MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr("203.0.113.7");
		return request;
	}
}
