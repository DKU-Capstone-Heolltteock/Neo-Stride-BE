package com.neostride.server.crew.service;

import com.neostride.server.auth.exception.ForbiddenException;
import com.neostride.server.crew.dto.CrewChatMessageRequest;
import com.neostride.server.crew.dto.CrewChatMessageResponse;
import com.neostride.server.crew.dto.CrewCreateRequest;
import com.neostride.server.crew.dto.CrewEventAttendanceRequest;
import com.neostride.server.crew.dto.CrewEventRequest;
import com.neostride.server.crew.dto.CrewEventResponse;
import com.neostride.server.crew.dto.CrewJoinResponse;
import com.neostride.server.crew.dto.CrewMemberResponse;
import com.neostride.server.crew.dto.CrewRankingEntry;
import com.neostride.server.crew.dto.CrewRankingResponse;
import com.neostride.server.crew.dto.CrewResponse;
import com.neostride.server.crew.dto.CrewUpdateRequest;
import com.neostride.server.crew.dto.InstantCrewApplicationResponse;
import com.neostride.server.crew.dto.InstantCrewRequest;
import com.neostride.server.crew.dto.InstantCrewResponse;
import com.neostride.server.crew.dto.InstantCrewStatusRequest;
import com.neostride.server.crew.repository.CrewRepository;
import com.neostride.server.crew.repository.CrewRepository.CrewEventRow;
import com.neostride.server.crew.repository.CrewRepository.CrewMembership;
import com.neostride.server.crew.repository.CrewRepository.InstantParticipant;
import com.neostride.server.platform.event.NotificationRequestedEvent;
import com.neostride.server.running.api.RunningAggregate;
import com.neostride.server.running.api.RunningStatsReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrewService {
	private static final int DEFAULT_LIST_LIMIT = 50;
	private static final int MAX_LIST_LIMIT = 100;
	private static final int MAX_MESSAGE_LIMIT = 100;

	private final CrewRepository crewRepository;
	private final RunningStatsReader runningStatsReader;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public CrewService(CrewRepository crewRepository, RunningStatsReader runningStatsReader,
			ApplicationEventPublisher eventPublisher) {
		this(crewRepository, runningStatsReader, eventPublisher, Clock.systemUTC());
	}

	CrewService(CrewRepository crewRepository, RunningStatsReader runningStatsReader,
			ApplicationEventPublisher eventPublisher, Clock clock) {
		this.crewRepository = crewRepository;
		this.runningStatsReader = runningStatsReader;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public CrewResponse createCrew(long userId, CrewCreateRequest request) {
		String name = required(request.name(), "크루 이름");
		String visibility = enumValue(request.visibility(), "PUBLIC", "PUBLIC", "PRIVATE");
		String joinPolicy = enumValue(request.joinPolicy(), "OPEN", "OPEN", "APPROVAL", "INVITE");
		long crewId = crewRepository.createCrew(
				userId,
				name,
				optional(request.description()),
				visibility,
				joinPolicy,
				optional(request.region()),
				optional(request.profileImageUrl())
		);
		crewRepository.addOwnerMember(crewId, userId);
		return getCrew(userId, crewId);
	}

	@Transactional(readOnly = true)
	public List<CrewResponse> listCrews(long userId, boolean mine, String region, String keyword, Integer limit) {
		return crewRepository.listCrews(userId, mine, optional(region), optional(keyword), limit(limit));
	}

	@Transactional(readOnly = true)
	public CrewResponse getCrew(long userId, long crewId) {
		CrewResponse crew = findCrewOrThrow(userId, crewId);
		if ("PRIVATE".equals(crew.visibility()) && !"ACCEPTED".equals(crew.viewerStatus())) {
			throw new ForbiddenException("비공개 크루는 가입한 멤버만 조회할 수 있습니다.");
		}
		return crew;
	}

	@Transactional
	public CrewResponse updateCrew(long userId, long crewId, CrewUpdateRequest request) {
		requireAdmin(crewId, userId);
		crewRepository.updateCrew(
				crewId,
				optional(request.name()),
				optional(request.description()),
				enumValueOrNull(request.visibility(), "PUBLIC", "PRIVATE"),
				enumValueOrNull(request.joinPolicy(), "OPEN", "APPROVAL", "INVITE"),
				optional(request.region()),
				optional(request.profileImageUrl())
		);
		return getCrew(userId, crewId);
	}

	@Transactional
	public CrewJoinResponse joinCrew(long userId, long crewId) {
		CrewResponse crew = findCrewOrThrow(userId, crewId);
		CrewMembership previous = crewRepository.findMembership(crewId, userId).orElse(null);
		if (previous != null && "ACCEPTED".equals(previous.status())) {
			return new CrewJoinResponse(crewId, userId, previous.role(), previous.status(), "이미 가입된 크루입니다.");
		}
		if ("INVITE".equals(crew.joinPolicy())) {
			throw new ForbiddenException("초대 전용 크루입니다.");
		}
		String status = "OPEN".equals(crew.joinPolicy()) ? "ACCEPTED" : "REQUESTED";
		crewRepository.saveMembership(crewId, userId, status);
		if ("ACCEPTED".equals(status) && !wasAccepted(previous)) {
			crewRepository.adjustMemberCount(crewId, 1);
		}
		if ("REQUESTED".equals(status)) {
			notifyCrewAdmins(crewId, userId, "CREW_JOIN_REQUEST", crew.name() + " 가입 요청이 도착했습니다.", "/api/crews/" + crewId + "/members");
		}
		return new CrewJoinResponse(crewId, userId, "MEMBER", status, "ACCEPTED".equals(status) ? "크루에 가입했습니다." : "가입 요청을 보냈습니다.");
	}

	@Transactional
	public CrewJoinResponse approveCrewMember(long actorUserId, long crewId, long targetUserId) {
		requireAdmin(crewId, actorUserId);
		CrewMembership previous = crewRepository.findMembership(crewId, targetUserId)
				.orElseThrow(() -> new IllegalArgumentException("가입 요청을 찾을 수 없습니다."));
		crewRepository.updateMemberStatus(crewId, targetUserId, "ACCEPTED");
		if (!wasAccepted(previous)) {
			crewRepository.adjustMemberCount(crewId, 1);
		}
		notifyUser(targetUserId, "CREW_JOIN_APPROVED", "크루 가입 요청이 승인되었습니다.", "/api/crews/" + crewId);
		return new CrewJoinResponse(crewId, targetUserId, previous.role(), "ACCEPTED", "가입 요청을 승인했습니다.");
	}

	@Transactional
	public CrewJoinResponse rejectCrewMember(long actorUserId, long crewId, long targetUserId) {
		requireAdmin(crewId, actorUserId);
		CrewMembership previous = crewRepository.findMembership(crewId, targetUserId)
				.orElseThrow(() -> new IllegalArgumentException("가입 요청을 찾을 수 없습니다."));
		crewRepository.updateMemberStatus(crewId, targetUserId, "REJECTED");
		if (wasAccepted(previous)) {
			crewRepository.adjustMemberCount(crewId, -1);
		}
		notifyUser(targetUserId, "CREW_JOIN_REJECTED", "크루 가입 요청이 거절되었습니다.", "/api/crews/" + crewId);
		return new CrewJoinResponse(crewId, targetUserId, previous.role(), "REJECTED", "가입 요청을 거절했습니다.");
	}

	@Transactional
	public CrewJoinResponse leaveCrew(long userId, long crewId) {
		CrewMembership membership = requireAcceptedMember(crewId, userId);
		if ("OWNER".equals(membership.role())) {
			throw new ForbiddenException("크루 소유자는 탈퇴할 수 없습니다.");
		}
		crewRepository.leaveCrew(crewId, userId);
		crewRepository.adjustMemberCount(crewId, -1);
		return new CrewJoinResponse(crewId, userId, membership.role(), "LEFT", "크루에서 탈퇴했습니다.");
	}

	@Transactional(readOnly = true)
	public List<CrewMemberResponse> listMembers(long userId, long crewId) {
		requireAcceptedMember(crewId, userId);
		return crewRepository.listAcceptedMembers(crewId);
	}

	@Transactional
	public CrewEventResponse createEvent(long userId, long crewId, CrewEventRequest request) {
		requireAdmin(crewId, userId);
		String title = required(request.title(), "일정 제목");
		LocalDateTime startsAt = requireDateTime(request.startsAt(), "starts_at");
		if (request.endsAt() != null && request.endsAt().isBefore(startsAt)) {
			throw new IllegalArgumentException("ends_at은 starts_at 이후여야 합니다.");
		}
		Integer capacity = positiveOrNull(request.capacity(), "capacity");
		String eventType = enumValue(request.eventType(), "OFFLINE", "OFFLINE", "VIRTUAL");
		long eventId = crewRepository.createEvent(
				crewId,
				userId,
				title,
				optional(request.description()),
				eventType,
				startsAt,
				request.endsAt(),
				optional(request.locationLabel()),
				optional(request.meetingPlace()),
				capacity
		);
		crewRepository.upsertEventParticipation(eventId, userId, "ACCEPTED");
		notifyEventCreated(crewId, userId, title);
		return crewRepository.findEventResponse(crewId, eventId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<CrewEventResponse> listEvents(long userId, long crewId) {
		requireAcceptedMember(crewId, userId);
		return crewRepository.listEvents(crewId);
	}

	@Transactional
	public CrewJoinResponse joinEvent(long userId, long crewId, long eventId) {
		requireAcceptedMember(crewId, userId);
		CrewEventRow event = crewRepository.findEvent(crewId, eventId)
				.orElseThrow(() -> new IllegalArgumentException("크루 일정을 찾을 수 없습니다."));
		if (!List.of("SCHEDULED", "IN_PROGRESS").contains(event.status())) {
			throw new IllegalArgumentException("참가할 수 없는 일정 상태입니다.");
		}
		if (event.capacity() != null && crewRepository.acceptedEventParticipantCount(eventId) >= event.capacity()) {
			throw new IllegalArgumentException("일정 참가 정원이 가득 찼습니다.");
		}
		crewRepository.upsertEventParticipation(eventId, userId, "ACCEPTED");
		return new CrewJoinResponse(crewId, userId, "MEMBER", "ACCEPTED", "일정에 참가했습니다.");
	}

	@Transactional
	public CrewEventResponse markEventAttendance(long actorUserId, long crewId, long eventId, CrewEventAttendanceRequest request) {
		requireAdmin(crewId, actorUserId);
		crewRepository.findEvent(crewId, eventId).orElseThrow(() -> new IllegalArgumentException("크루 일정을 찾을 수 없습니다."));
		long targetUserId = positiveLong(request.userId(), "user_id");
		requireAcceptedMember(crewId, targetUserId);
		String status = enumValue(request.status(), "ATTENDED", "ACCEPTED", "DECLINED", "CANCELLED", "ATTENDED");
		crewRepository.markEventAttendance(eventId, targetUserId, status, request.runningRecordId());
		return crewRepository.findEventResponse(crewId, eventId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public CrewRankingResponse rankings(long userId, long crewId, String period) {
		requireAcceptedMember(crewId, userId);
		RankingPeriod rankingPeriod = rankingPeriod(period);
		List<CrewMemberResponse> members = crewRepository.listAcceptedMembers(crewId);
		List<Long> userIds = members.stream().map(CrewMemberResponse::userId).toList();
		Map<Long, RunningAggregate> running = runningStatsReader.summarizeByUsers(userIds, rankingPeriod.from(), rankingPeriod.to());
		Map<Long, Long> attendance = crewRepository.attendanceCounts(crewId, userIds, rankingPeriod.from(), rankingPeriod.to());
		List<RankingRow> rows = members.stream()
				.map(member -> new RankingRow(
						member,
						running.getOrDefault(member.userId(), new RunningAggregate(member.userId(), BigDecimal.ZERO, 0)),
						attendance.getOrDefault(member.userId(), 0L)
				))
				.sorted(Comparator
						.<RankingRow, BigDecimal>comparing(row -> row.runningAggregate().totalDistanceKm()).reversed()
						.thenComparing(Comparator.<RankingRow>comparingLong(row -> row.runningAggregate().runCount()).reversed())
						.thenComparing(Comparator.<RankingRow>comparingLong(RankingRow::attendanceCount).reversed())
						.thenComparing(row -> row.member().userId()))
				.toList();
		List<CrewRankingEntry> entries = new java.util.ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			RankingRow row = rows.get(index);
			entries.add(new CrewRankingEntry(
					index + 1,
					row.member().userId(),
					row.member().nickname(),
					row.member().profileImageUrl(),
					row.runningAggregate().totalDistanceKm(),
					row.runningAggregate().runCount(),
					row.attendanceCount()
			));
		}
		return new CrewRankingResponse(
				crewId,
				rankingPeriod.name(),
				rankingPeriod.from() == null ? null : rankingPeriod.from().toString(),
				rankingPeriod.to() == null ? null : rankingPeriod.to().toString(),
				entries
		);
	}

	@Transactional
	public InstantCrewResponse createInstantCrew(long userId, InstantCrewRequest request) {
		Long crewId = request.crewId();
		if (crewId != null) {
			requireAcceptedMember(crewId, userId);
		}
		LocalDateTime startsAt = requireDateTime(request.startsAt(), "starts_at");
		LocalDateTime recruitUntil = requireDateTime(request.recruitUntil(), "recruit_until");
		LocalDateTime now = LocalDateTime.now(clock);
		if (startsAt.isBefore(now)) {
			throw new IllegalArgumentException("starts_at은 현재 이후여야 합니다.");
		}
		if (recruitUntil.isBefore(now) || recruitUntil.isAfter(now.plusHours(24)) || recruitUntil.isAfter(startsAt)) {
			throw new IllegalArgumentException("recruit_until은 현재 이후, 시작 전, 24시간 이내여야 합니다.");
		}
		int capacity = positiveOrNull(request.capacity(), "capacity") == null ? 2 : request.capacity();
		long instantCrewId = crewRepository.createInstantCrew(
				crewId,
				userId,
				required(request.title(), "번개 크루 제목"),
				optional(request.description()),
				required(request.region(), "region"),
				required(request.locationLabel(), "location_label"),
				required(request.meetingPlace(), "meeting_place"),
				startsAt,
				recruitUntil,
				capacity
		);
		crewRepository.saveInstantParticipant(instantCrewId, userId, "ACCEPTED");
		return getInstantCrew(userId, instantCrewId);
	}

	@Transactional(readOnly = true)
	public List<InstantCrewResponse> listInstantCrews(long userId, String region, Integer limit) {
		return crewRepository.listInstantCrews(userId, optional(region), limit(limit));
	}

	@Transactional(readOnly = true)
	public InstantCrewResponse getInstantCrew(long userId, long instantCrewId) {
		InstantCrewResponse response = crewRepository.findInstantCrew(instantCrewId, userId)
				.orElseThrow(() -> new IllegalArgumentException("번개 크루를 찾을 수 없습니다."));
		return applyInstantPrivacy(response, userId);
	}

	@Transactional
	public InstantCrewApplicationResponse applyInstantCrew(long userId, long instantCrewId) {
		InstantCrewResponse instantCrew = getInstantCrew(userId, instantCrewId);
		if (!"OPEN".equals(instantCrew.status())) {
			throw new IllegalArgumentException("모집 중인 번개 크루만 신청할 수 있습니다.");
		}
		if (LocalDateTime.parse(instantCrew.recruitUntil()).isBefore(LocalDateTime.now(clock))) {
			throw new IllegalArgumentException("모집 시간이 지난 번개 크루입니다.");
		}
		InstantParticipant previous = crewRepository.findInstantParticipant(instantCrewId, userId).orElse(null);
		if (previous != null && "ACCEPTED".equals(previous.status())) {
			return new InstantCrewApplicationResponse(instantCrewId, userId, "ACCEPTED", "이미 참가 중입니다.");
		}
		crewRepository.saveInstantParticipant(instantCrewId, userId, "REQUESTED");
		notifyUser(instantCrew.hostUserId(), "INSTANT_CREW_REQUEST", "번개 크루 참가 신청이 도착했습니다.", "/api/instant-crews/" + instantCrewId);
		return new InstantCrewApplicationResponse(instantCrewId, userId, "REQUESTED", "참가 신청을 보냈습니다.");
	}

	@Transactional
	public InstantCrewApplicationResponse approveInstantParticipant(long actorUserId, long instantCrewId, long targetUserId) {
		InstantCrewResponse instantCrew = requireInstantHost(actorUserId, instantCrewId);
		if (crewRepository.acceptedInstantParticipantCount(instantCrewId) >= instantCrew.capacity()) {
			throw new IllegalArgumentException("번개 크루 참가 정원이 가득 찼습니다.");
		}
		crewRepository.findInstantParticipant(instantCrewId, targetUserId)
				.orElseThrow(() -> new IllegalArgumentException("참가 신청을 찾을 수 없습니다."));
		crewRepository.updateInstantParticipantStatus(instantCrewId, targetUserId, "ACCEPTED");
		notifyUser(targetUserId, "INSTANT_CREW_APPROVED", "번개 크루 참가 신청이 승인되었습니다.", "/api/instant-crews/" + instantCrewId);
		return new InstantCrewApplicationResponse(instantCrewId, targetUserId, "ACCEPTED", "참가 신청을 승인했습니다.");
	}

	@Transactional
	public InstantCrewApplicationResponse rejectInstantParticipant(long actorUserId, long instantCrewId, long targetUserId) {
		requireInstantHost(actorUserId, instantCrewId);
		crewRepository.findInstantParticipant(instantCrewId, targetUserId)
				.orElseThrow(() -> new IllegalArgumentException("참가 신청을 찾을 수 없습니다."));
		crewRepository.updateInstantParticipantStatus(instantCrewId, targetUserId, "REJECTED");
		notifyUser(targetUserId, "INSTANT_CREW_REJECTED", "번개 크루 참가 신청이 거절되었습니다.", "/api/instant-crews/" + instantCrewId);
		return new InstantCrewApplicationResponse(instantCrewId, targetUserId, "REJECTED", "참가 신청을 거절했습니다.");
	}

	@Transactional
	public InstantCrewResponse updateInstantStatus(long actorUserId, long instantCrewId, InstantCrewStatusRequest request) {
		requireInstantHost(actorUserId, instantCrewId);
		String status = enumValue(request.status(), null, "OPEN", "CLOSED", "CANCELLED", "COMPLETED");
		crewRepository.updateInstantCrewStatus(instantCrewId, status);
		return getInstantCrew(actorUserId, instantCrewId);
	}

	@Transactional
	public CrewChatMessageResponse sendCrewMessage(long userId, long crewId, CrewChatMessageRequest request) {
		requireAcceptedMember(crewId, userId);
		long messageId = crewRepository.insertCrewChatMessage(crewId, userId, chatText(request));
		return crewRepository.findChatMessage(messageId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<CrewChatMessageResponse> listCrewMessages(long userId, long crewId, Long beforeMessageId, Integer limit) {
		requireAcceptedMember(crewId, userId);
		return crewRepository.listCrewMessages(crewId, beforeMessageId, messageLimit(limit));
	}

	@Transactional
	public CrewChatMessageResponse sendInstantMessage(long userId, long instantCrewId, CrewChatMessageRequest request) {
		requireInstantChatMember(userId, instantCrewId);
		long messageId = crewRepository.insertInstantChatMessage(instantCrewId, userId, chatText(request));
		return crewRepository.findChatMessage(messageId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<CrewChatMessageResponse> listInstantMessages(long userId, long instantCrewId, Long beforeMessageId, Integer limit) {
		requireInstantChatMember(userId, instantCrewId);
		return crewRepository.listInstantMessages(instantCrewId, beforeMessageId, messageLimit(limit));
	}

	private CrewResponse findCrewOrThrow(long userId, long crewId) {
		return crewRepository.findCrew(crewId, userId)
				.orElseThrow(() -> new IllegalArgumentException("크루를 찾을 수 없습니다."));
	}

	private CrewMembership requireAcceptedMember(long crewId, long userId) {
		CrewMembership membership = crewRepository.findMembership(crewId, userId)
				.orElseThrow(() -> new ForbiddenException("크루 멤버만 사용할 수 있습니다."));
		if (!"ACCEPTED".equals(membership.status())) {
			throw new ForbiddenException("크루 멤버만 사용할 수 있습니다.");
		}
		return membership;
	}

	private CrewMembership requireAdmin(long crewId, long userId) {
		CrewMembership membership = requireAcceptedMember(crewId, userId);
		if (!List.of("OWNER", "ADMIN").contains(membership.role())) {
			throw new ForbiddenException("크루 관리자만 사용할 수 있습니다.");
		}
		return membership;
	}

	private InstantCrewResponse requireInstantHost(long userId, long instantCrewId) {
		InstantCrewResponse response = crewRepository.findInstantCrew(instantCrewId, userId)
				.orElseThrow(() -> new IllegalArgumentException("번개 크루를 찾을 수 없습니다."));
		if (response.hostUserId() != userId) {
			throw new ForbiddenException("번개 크루 호스트만 사용할 수 있습니다.");
		}
		return response;
	}

	private void requireInstantChatMember(long userId, long instantCrewId) {
		InstantCrewResponse response = crewRepository.findInstantCrew(instantCrewId, userId)
				.orElseThrow(() -> new IllegalArgumentException("번개 크루를 찾을 수 없습니다."));
		if (response.hostUserId() == userId) {
			return;
		}
		if (!"ACCEPTED".equals(response.viewerStatus())) {
			throw new ForbiddenException("승인된 번개 크루 참가자만 사용할 수 있습니다.");
		}
	}

	private InstantCrewResponse applyInstantPrivacy(InstantCrewResponse response, long viewerUserId) {
		if (response.hostUserId() == viewerUserId || "ACCEPTED".equals(response.viewerStatus())) {
			return response;
		}
		return new InstantCrewResponse(
				response.instantCrewId(),
				response.crewId(),
				response.hostUserId(),
				response.title(),
				response.description(),
				response.status(),
				response.region(),
				response.locationLabel(),
				null,
				response.startsAt(),
				response.recruitUntil(),
				response.capacity(),
				response.participantCount(),
				response.viewerStatus(),
				response.createdAt()
		);
	}

	private void notifyCrewAdmins(long crewId, long actorUserId, String type, String message, String endpoint) {
		crewRepository.listAcceptedMembers(crewId).stream()
				.filter(member -> List.of("OWNER", "ADMIN").contains(member.role()))
				.filter(member -> member.userId() != actorUserId)
				.forEach(member -> notifyUser(member.userId(), type, message, endpoint));
	}

	private void notifyEventCreated(long crewId, long actorUserId, String title) {
		String endpoint = "/api/crews/" + crewId + "/events";
		crewRepository.listAcceptedMembers(crewId).stream()
				.filter(member -> member.userId() != actorUserId)
				.forEach(member -> notifyUser(member.userId(), "CREW_EVENT_CREATED", "새 크루 일정이 등록되었습니다: " + title, endpoint));
	}

	private void notifyUser(long userId, String type, String message, String endpoint) {
		eventPublisher.publishEvent(new NotificationRequestedEvent(userId, type, message, endpoint));
	}

	private RankingPeriod rankingPeriod(String rawPeriod) {
		String period = enumValue(rawPeriod, "weekly", "weekly", "monthly", "all").toLowerCase(Locale.ROOT);
		LocalDate today = LocalDate.now(clock);
		return switch (period) {
			case "monthly" -> {
				LocalDate from = today.withDayOfMonth(1);
				yield new RankingPeriod("monthly", from, from.plusMonths(1).minusDays(1));
			}
			case "all" -> new RankingPeriod("all", null, null);
			default -> {
				LocalDate from = today.minusDays(today.getDayOfWeek().getValue() - 1L);
				yield new RankingPeriod("weekly", from, from.plusDays(6));
			}
		};
	}

	private String chatText(CrewChatMessageRequest request) {
		String text = required(request.messageText(), "message_text");
		if (text.length() > 1000) {
			throw new IllegalArgumentException("message_text는 1000자 이하여야 합니다.");
		}
		return text;
	}

	private static String required(String value, String field) {
		String trimmed = optional(value);
		if (trimmed == null) {
			throw new IllegalArgumentException(field + "는 필수입니다.");
		}
		return trimmed;
	}

	private static String optional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private static LocalDateTime requireDateTime(LocalDateTime value, String field) {
		if (value == null) {
			throw new IllegalArgumentException(field + "는 필수입니다.");
		}
		return value;
	}

	private static Integer positiveOrNull(Integer value, String field) {
		if (value == null) {
			return null;
		}
		if (value <= 0) {
			throw new IllegalArgumentException(field + "는 1 이상이어야 합니다.");
		}
		return value;
	}

	private static long positiveLong(Long value, String field) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(field + "는 1 이상이어야 합니다.");
		}
		return value;
	}

	private static int limit(Integer value) {
		if (value == null) {
			return DEFAULT_LIST_LIMIT;
		}
		if (value <= 0) {
			throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
		}
		return Math.min(value, MAX_LIST_LIMIT);
	}

	private static int messageLimit(Integer value) {
		if (value == null) {
			return DEFAULT_LIST_LIMIT;
		}
		if (value <= 0) {
			throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
		}
		return Math.min(value, MAX_MESSAGE_LIMIT);
	}

	private static String enumValueOrNull(String raw, String... allowed) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return enumValue(raw, null, allowed);
	}

	private static String enumValue(String raw, String defaultValue, String... allowed) {
		String value = raw == null || raw.isBlank() ? defaultValue : raw.trim();
		if (value == null) {
			throw new IllegalArgumentException("값이 필요합니다.");
		}
		String normalized = value.toUpperCase(Locale.ROOT);
		for (String candidate : allowed) {
			if (normalized.equals(candidate.toUpperCase(Locale.ROOT))) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("허용되지 않는 값입니다: " + raw);
	}

	private static boolean wasAccepted(CrewMembership membership) {
		return membership != null && "ACCEPTED".equals(membership.status());
	}

	private record RankingPeriod(String name, LocalDate from, LocalDate to) {}

	private record RankingRow(CrewMemberResponse member, RunningAggregate runningAggregate, long attendanceCount) {}
}
