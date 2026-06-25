package com.neostride.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neostride.server.platform.event.NotificationRequestedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CommunityBadgeServiceTest {
	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final CommunityBadgeService service = new CommunityBadgeService(jdbcTemplate, eventPublisher);

	@Test
	void improveBadgeIfHigherStoresBadgeAndPublishesNotification() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of("SILVER"));

		service.improveBadgeIfHigher(7L, "gold");

		verify(jdbcTemplate).update(anyString(), eq("GOLD"), eq(7L));
		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue()).isInstanceOf(NotificationRequestedEvent.class);
		NotificationRequestedEvent event = (NotificationRequestedEvent) eventCaptor.getValue();
		assertThat(event.userId()).isEqualTo(7L);
		assertThat(event.type()).isEqualTo("GRADE");
		assertThat(event.message()).isEqualTo("GOLD 배지를 달성했습니다.");
		assertThat(event.endpoint()).isEqualTo("/users/me/badge");
	}

	@Test
	void improveBadgeIfHigherSkipsSameOrLowerBadge() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of("GOLD"));

		service.improveBadgeIfHigher(7L, "silver");

		verify(jdbcTemplate, never()).update(anyString(), any(Object.class), any(Object.class));
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void improveBadgeIfHigherSkipsUnknownBadge() {
		service.improveBadgeIfHigher(7L, "unknown");

		verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object.class));
		verify(jdbcTemplate, never()).update(anyString(), any(Object.class), any(Object.class));
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void improveBadgeIfHigherSkipsDeletedOrMissingUser() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of());

		service.improveBadgeIfHigher(7L, "gold");

		verify(jdbcTemplate, never()).update(anyString(), any(Object.class), any(Object.class));
		verify(eventPublisher, never()).publishEvent(any());
	}
}
