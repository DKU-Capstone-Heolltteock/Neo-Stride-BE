package com.neostride.server.auth.service;

import com.neostride.server.auth.api.AdminUserAccount;
import com.neostride.server.auth.api.UserAdministrationPort;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuspendedAccountAccessFilterTest {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneOffset.UTC);
	private static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
	private static final String SECRET = "test-secret-for-suspended-account-filter";

	private final JwtTokenService tokenService = new JwtTokenService(SECRET, 3600, 1209600);
	private final UserAdministrationPort userAdministrationPort = mock(UserAdministrationPort.class);
	private final SuspendedAccountAccessFilter filter = new SuspendedAccountAccessFilter(tokenService, userAdministrationPort, CLOCK);

	@Test
	void permanentSuspensionBlocksApiAccess() throws Exception {
		FilterChain chain = mock(FilterChain.class);
		when(userAdministrationPort.findAccount(7L)).thenReturn(Optional.of(account(null)));
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request(), response, chain);

		assertThat(response.getStatus()).isEqualTo(403);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void futureTemporarySuspensionBlocksApiAccess() throws Exception {
		FilterChain chain = mock(FilterChain.class);
		when(userAdministrationPort.findAccount(7L)).thenReturn(Optional.of(account(NOW.plusMinutes(1))));
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request(), response, chain);

		assertThat(response.getStatus()).isEqualTo(403);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void expiredTemporarySuspensionAllowsApiAccess() throws Exception {
		FilterChain chain = mock(FilterChain.class);
		when(userAdministrationPort.findAccount(7L)).thenReturn(Optional.of(account(NOW.minusSeconds(1))));
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request(), response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void permanentSuspensionBlocksLegacyFeedUpload() throws Exception {
		FilterChain chain = mock(FilterChain.class);
		when(userAdministrationPort.findAccount(7L)).thenReturn(Optional.of(account(null)));
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("POST", "/feeds"), response, chain);

		assertThat(response.getStatus()).isEqualTo(403);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void permanentSuspensionBlocksLegacyStatusUpdate() throws Exception {
		FilterChain chain = mock(FilterChain.class);
		when(userAdministrationPort.findAccount(7L)).thenReturn(Optional.of(account(null)));
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request("PATCH", "/users/me/status"), response, chain);

		assertThat(response.getStatus()).isEqualTo(403);
		verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private MockHttpServletRequest request() {
		return request("GET", "/api/community/feeds");
	}

	private MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.addHeader("Authorization", "Bearer " + tokenService.generateAccessToken(7L, "runner@example.com", "러너"));
		return request;
	}

	private AdminUserAccount account(LocalDateTime suspendedUntil) {
		return new AdminUserAccount(
				7L,
				"runner@example.com",
				"러너",
				"runner",
				null,
				"SUSPENDED",
				NOW.minusDays(1),
				suspendedUntil,
				"policy",
				NOW.minusMonths(1),
				NOW.minusDays(1)
		);
	}
}
