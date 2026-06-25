package com.neostride.server.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neostride.server.auth.repository.RefreshTokenRepository;
import com.neostride.server.auth.repository.UserRepository;
import com.neostride.server.platform.event.UserSoftDeletedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class UserAccountLifecycleServiceTest {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC);
	private static final LocalDateTime DELETED_AT = LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());

	private final UserRepository userRepository = mock(UserRepository.class);
	private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final UserAccountLifecycleService service = new UserAccountLifecycleService(
			userRepository,
			refreshTokenRepository,
			eventPublisher,
			CLOCK
	);

	@Test
	void deleteAccountSoftDeletesUserRevokesRefreshTokensAndPublishesEvent() {
		when(userRepository.softDelete(7L, DELETED_AT, "USER_WITHDRAWAL")).thenReturn(true);

		service.deleteAccount(7L);

		verify(userRepository).softDelete(7L, DELETED_AT, "USER_WITHDRAWAL");
		verify(refreshTokenRepository).revokeAllForUser(7L);
		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue()).isInstanceOf(UserSoftDeletedEvent.class);
		UserSoftDeletedEvent event = (UserSoftDeletedEvent) eventCaptor.getValue();
		assertThat(event.userId()).isEqualTo(7L);
		assertThat(event.deletedAt()).isEqualTo(DELETED_AT);
	}

	@Test
	void deleteAccountDoesNotRevokeOrPublishWhenUserIsMissingOrAlreadyDeleted() {
		when(userRepository.softDelete(7L, DELETED_AT, "USER_WITHDRAWAL")).thenReturn(false);

		assertThatThrownBy(() -> service.deleteAccount(7L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("사용자를 찾을 수 없습니다.");

		verify(refreshTokenRepository, never()).revokeAllForUser(anyLong());
		verify(eventPublisher, never()).publishEvent(any());
	}
}
