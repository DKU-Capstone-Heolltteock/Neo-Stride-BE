package com.neostride.server.config;

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

class RateLimitFilterTest {

	@Test
	void authBucketReturnsTooManyRequestsAfterLimit() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(true, 1, 10, 10, Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneOffset.UTC));
		FilterChain chain = mock(FilterChain.class);

		MockHttpServletResponse firstResponse = new MockHttpServletResponse();
		filter.doFilterInternal(request("POST", "/api/auth/login"), firstResponse, chain);

		MockHttpServletResponse secondResponse = new MockHttpServletResponse();
		filter.doFilterInternal(request("POST", "/api/auth/login"), secondResponse, chain);

		verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(secondResponse.getStatus()).isEqualTo(429);
		assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
	}

	@Test
	void disabledFilterAlwaysPassesThrough() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(false, 1, 1, 1, Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneOffset.UTC));
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request("POST", "/api/auth/signup"), new MockHttpServletResponse(), chain);
		filter.doFilterInternal(request("POST", "/api/auth/signup"), new MockHttpServletResponse(), chain);

		verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void writeBucketCoversLegacyAndNotificationEndpoints() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(true, 10, 1, 10, Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneOffset.UTC));
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request("POST", "/feeds"), new MockHttpServletResponse(), chain);
		MockHttpServletResponse secondResponse = new MockHttpServletResponse();
		filter.doFilterInternal(request("DELETE", "/api/notifications/7"), secondResponse, chain);

		verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(secondResponse.getStatus()).isEqualTo(429);
	}

	@Test
	void untrustedForwardedForDoesNotResetClientBucket() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(true, 1, 10, 10, Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneOffset.UTC));
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletRequest first = request("POST", "/api/auth/login");
		first.addHeader("X-Forwarded-For", "198.51.100.10");
		MockHttpServletRequest second = request("POST", "/api/auth/login");
		second.addHeader("X-Forwarded-For", "198.51.100.11");

		filter.doFilterInternal(first, new MockHttpServletResponse(), chain);
		MockHttpServletResponse secondResponse = new MockHttpServletResponse();
		filter.doFilterInternal(second, secondResponse, chain);

		assertThat(secondResponse.getStatus()).isEqualTo(429);
	}

	@Test
	void trustedProxyForwardedForSeparatesClientBuckets() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(true, 1, 10, 10, Set.of("203.0.113.7"), Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneOffset.UTC));
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletRequest first = request("POST", "/api/auth/login");
		first.addHeader("X-Forwarded-For", "198.51.100.10");
		MockHttpServletRequest second = request("POST", "/api/auth/login");
		second.addHeader("X-Forwarded-For", "198.51.100.11");
		MockHttpServletResponse secondResponse = new MockHttpServletResponse();

		filter.doFilterInternal(first, new MockHttpServletResponse(), chain);
		filter.doFilterInternal(second, secondResponse, chain);

		verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(secondResponse.getStatus()).isEqualTo(200);
	}

	@Test
	void writeBucketCoversCrewEndpoints() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(true, 10, 1, 10, Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneOffset.UTC));
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request("POST", "/api/crews"), new MockHttpServletResponse(), chain);
		MockHttpServletResponse secondResponse = new MockHttpServletResponse();
		filter.doFilterInternal(request("POST", "/api/instant-crews"), secondResponse, chain);

		verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThat(secondResponse.getStatus()).isEqualTo(429);
	}

	private static MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr("203.0.113.7");
		return request;
	}
}
