package com.neostride.server.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neostride.server.crew.dto.CrewJoinResponse;
import com.neostride.server.crew.dto.CrewMemberResponse;
import com.neostride.server.crew.dto.CrewRankingResponse;
import com.neostride.server.crew.dto.CrewResponse;
import com.neostride.server.crew.dto.InstantCrewResponse;
import com.neostride.server.crew.repository.CrewRepository;
import com.neostride.server.crew.repository.CrewRepository.CrewMembership;
import com.neostride.server.platform.event.NotificationRequestedEvent;
import com.neostride.server.running.api.RunningAggregate;
import com.neostride.server.running.api.RunningStatsReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CrewServiceTest {
	private CrewRepository crewRepository;
	private RunningStatsReader runningStatsReader;
	private ApplicationEventPublisher eventPublisher;
	private CrewService crewService;

	@BeforeEach
	void setUp() {
		crewRepository = mock(CrewRepository.class);
		runningStatsReader = mock(RunningStatsReader.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		crewService = new CrewService(
				crewRepository,
				runningStatsReader,
				eventPublisher,
				Clock.fixed(Instant.parse("2026-06-18T00:00:00Z"), ZoneOffset.UTC)
		);
	}

	@Test
	void openCrewJoinAcceptsImmediatelyAndIncrementsMemberCount() {
		when(crewRepository.findCrew(10L, 2L)).thenReturn(Optional.of(crew("OPEN", null)));
		when(crewRepository.findMembership(10L, 2L)).thenReturn(Optional.empty());

		CrewJoinResponse response = crewService.joinCrew(2L, 10L);

		assertThat(response.status()).isEqualTo("ACCEPTED");
		verify(crewRepository).saveMembership(10L, 2L, "ACCEPTED");
		verify(crewRepository).adjustMemberCount(10L, 1);
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void approvalCrewJoinCreatesRequestAndNotifiesAdmins() {
		when(crewRepository.findCrew(10L, 2L)).thenReturn(Optional.of(crew("APPROVAL", null)));
		when(crewRepository.findMembership(10L, 2L)).thenReturn(Optional.empty());
		when(crewRepository.listAcceptedMembers(10L)).thenReturn(List.of(member(1L, "OWNER")));

		CrewJoinResponse response = crewService.joinCrew(2L, 10L);

		assertThat(response.status()).isEqualTo("REQUESTED");
		verify(crewRepository).saveMembership(10L, 2L, "REQUESTED");
		verify(crewRepository, never()).adjustMemberCount(10L, 1);
		verify(eventPublisher).publishEvent(any(NotificationRequestedEvent.class));
	}

	@Test
	void instantCrewDetailHidesMeetingPlaceUntilAccepted() {
		when(crewRepository.findInstantCrew(30L, 2L)).thenReturn(Optional.of(new InstantCrewResponse(
				30L,
				null,
				1L,
				"퇴근런",
				"한강 5km",
				"OPEN",
				"서울",
				"여의도",
				"여의나루역 2번 출구",
				"2026-06-18T12:00:00",
				"2026-06-18T10:00:00",
				5,
				1,
				"REQUESTED",
				"2026-06-18T00:00:00"
		)));

		InstantCrewResponse response = crewService.getInstantCrew(2L, 30L);

		assertThat(response.meetingPlace()).isNull();
	}

	@Test
	void rankingsReadRunningDataThroughPort() {
		when(crewRepository.findMembership(10L, 1L)).thenReturn(Optional.of(new CrewMembership(10L, 1L, "OWNER", "ACCEPTED")));
		when(crewRepository.listAcceptedMembers(10L)).thenReturn(List.of(
				member(1L, "OWNER"),
				member(2L, "MEMBER")
		));
		LocalDate from = LocalDate.parse("2026-06-15");
		LocalDate to = LocalDate.parse("2026-06-21");
		when(runningStatsReader.summarizeByUsers(List.of(1L, 2L), from, to)).thenReturn(Map.of(
				1L, new RunningAggregate(1L, new BigDecimal("5.00"), 1L),
				2L, new RunningAggregate(2L, new BigDecimal("12.50"), 2L)
		));
		when(crewRepository.attendanceCounts(10L, List.of(1L, 2L), from, to)).thenReturn(Map.of(1L, 1L, 2L, 0L));

		CrewRankingResponse response = crewService.rankings(1L, 10L, "weekly");

		assertThat(response.entries()).extracting("userId").containsExactly(2L, 1L);
		verify(runningStatsReader).summarizeByUsers(List.of(1L, 2L), from, to);
	}

	private static CrewResponse crew(String joinPolicy, String viewerStatus) {
		return new CrewResponse(
				10L,
				1L,
				"네오 러너스",
				"주말 러닝 크루",
				"PUBLIC",
				joinPolicy,
				"서울",
				null,
				1,
				null,
				viewerStatus,
				"2026-06-18T00:00:00",
				"2026-06-18T00:00:00"
		);
	}

	private static CrewMemberResponse member(long userId, String role) {
		return new CrewMemberResponse(
				10L,
				userId,
				"runner" + userId,
				null,
				role,
				"ACCEPTED",
				"2026-06-18T00:00:00"
		);
	}
}
