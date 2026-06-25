package com.neostride.server.auth.service;

import com.neostride.server.auth.api.UserAccountLifecyclePort;
import com.neostride.server.auth.repository.RefreshTokenRepository;
import com.neostride.server.auth.repository.UserRepository;
import com.neostride.server.platform.event.UserSoftDeletedEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountLifecycleService implements UserAccountLifecyclePort {
	private static final String USER_WITHDRAWAL_REASON = "USER_WITHDRAWAL";

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public UserAccountLifecycleService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			ApplicationEventPublisher eventPublisher
	) {
		this(userRepository, refreshTokenRepository, eventPublisher, Clock.systemDefaultZone());
	}

	UserAccountLifecycleService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			ApplicationEventPublisher eventPublisher,
			Clock clock
	) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void deleteAccount(long userId) {
		if (userId <= 0) {
			throw new IllegalArgumentException("user_id는 1 이상의 값이어야 합니다.");
		}
		LocalDateTime deletedAt = LocalDateTime.now(clock);
		if (!userRepository.softDelete(userId, deletedAt, USER_WITHDRAWAL_REASON)) {
			throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
		}
		refreshTokenRepository.revokeAllForUser(userId);
		eventPublisher.publishEvent(new UserSoftDeletedEvent(userId, deletedAt));
	}
}
