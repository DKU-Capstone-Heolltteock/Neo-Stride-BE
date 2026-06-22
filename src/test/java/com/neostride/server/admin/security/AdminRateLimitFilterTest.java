package com.neostride.server.admin.security;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

	private MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr("203.0.113.7");
		return request;
	}
}
