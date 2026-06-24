package com.neostride.server.admin.security;

import com.neostride.server.platform.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminConsoleAccessFilterTest {
	@Test
	void delegatesConsolePathsToExposureGuard() throws Exception {
		AdminConsoleAccessFilter filter = new AdminConsoleAccessFilter(
				false,
				true,
				AdminConsoleAccessFilter.parseAllowedIpRanges("198.51.100.0/24"),
				new ClientIpResolver(Set.of())
		);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("POST", "/api/admin;anything/auth/login", "203.0.113.7"), response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void parsesExactAndCidrRangesForExposureGuardCompatibility() {
		var ranges = AdminConsoleAccessFilter.parseAllowedIpRanges("198.51.100.7, 203.0.113.0/24");

		assertThat(ranges).hasSize(2);
		assertThat(ranges.get(0).contains("198.51.100.7")).isTrue();
		assertThat(ranges.get(1).contains("203.0.113.40")).isTrue();
	}

	private static MockHttpServletRequest request(String method, String path, String remoteAddr) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(remoteAddr);
		return request;
	}
}
