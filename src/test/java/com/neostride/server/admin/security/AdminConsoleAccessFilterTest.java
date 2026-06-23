package com.neostride.server.admin.security;

import com.neostride.server.platform.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminConsoleAccessFilterTest {
	@Test
	void disabledConsoleRejectsMatrixParameterAdminPath() throws Exception {
		AdminConsoleAccessFilter filter = new AdminConsoleAccessFilter(
				false,
				false,
				AdminConsoleAccessFilter.parseAllowedIpRanges(""),
				new ClientIpResolver(Set.of())
		);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("POST", "/api/admin;anything/auth/login", "203.0.113.7"), response, chain);

		assertThat(response.getStatus()).isEqualTo(404);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void matrixParameterAdminPathRequiresAllowlist() throws Exception {
		AdminConsoleAccessFilter filter = new AdminConsoleAccessFilter(
				true,
				true,
				AdminConsoleAccessFilter.parseAllowedIpRanges("198.51.100.0/24"),
				new ClientIpResolver(Set.of())
		);
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("POST", "/api/admin;anything/auth/login", "203.0.113.7"), response, chain);

		assertThat(response.getStatus()).isEqualTo(403);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private static MockHttpServletRequest request(String method, String path, String remoteAddr) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(remoteAddr);
		return request;
	}
}
