package com.neostride.server.crew.repository;

import com.neostride.server.crew.dto.CrewChatMessageResponse;
import com.neostride.server.crew.dto.CrewEventResponse;
import com.neostride.server.crew.dto.CrewMemberRequestResponse;
import com.neostride.server.crew.dto.CrewMemberResponse;
import com.neostride.server.crew.dto.CrewResponse;
import com.neostride.server.crew.dto.InstantCrewParticipantRequestResponse;
import com.neostride.server.crew.dto.InstantCrewParticipantResponse;
import com.neostride.server.crew.dto.InstantCrewResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CrewRepository {
	private static final DateTimeFormatter RESPONSE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final JdbcTemplate jdbcTemplate;

	public CrewRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public long createCrew(long ownerUserId, String name, String description, String visibility,
			String joinPolicy, String region, String profileImageUrl) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO crews
						(owner_user_id, name, description, visibility, join_policy, region, profile_image_url, member_count)
					VALUES (?, ?, ?, ?, ?, ?, ?, 1)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, ownerUserId);
			ps.setString(2, name);
			ps.setString(3, description);
			ps.setString(4, visibility);
			ps.setString(5, joinPolicy);
			ps.setString(6, region);
			ps.setString(7, profileImageUrl);
			return ps;
		}, keyHolder);
		return generatedId(keyHolder, "크루 ID를 생성하지 못했습니다.");
	}

	public void addOwnerMember(long crewId, long ownerUserId) {
		jdbcTemplate.update("""
				INSERT INTO crew_members
					(crew_id, user_id, role, status, requested_at, responded_at, joined_at)
				VALUES (?, ?, 'OWNER', 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""", crewId, ownerUserId);
	}

	public int updateCrew(long crewId, String name, String description, String visibility,
			String joinPolicy, String region, String profileImageUrl) {
		return jdbcTemplate.update("""
				UPDATE crews
				SET name = COALESCE(?, name),
					description = COALESCE(?, description),
					visibility = COALESCE(?, visibility),
					join_policy = COALESCE(?, join_policy),
					region = COALESCE(?, region),
					profile_image_url = COALESCE(?, profile_image_url)
				WHERE crew_id = ?
				""", name, description, visibility, joinPolicy, region, profileImageUrl, crewId);
	}

	public int deleteCrew(long crewId) {
		return jdbcTemplate.update("DELETE FROM crews WHERE crew_id = ?", crewId);
	}

	public Optional<CrewResponse> findCrew(long crewId, long viewerUserId) {
		List<CrewResponse> rows = jdbcTemplate.query("""
				SELECT c.crew_id, c.owner_user_id, c.name, c.description, c.visibility, c.join_policy,
					c.region, c.profile_image_url, c.member_count, c.created_at, c.updated_at,
					viewer.role AS viewer_role, viewer.status AS viewer_status
				FROM crews c
				LEFT JOIN crew_members viewer ON viewer.crew_id = c.crew_id AND viewer.user_id = ?
				WHERE c.crew_id = ?
				""", (rs, rowNum) -> new CrewResponse(
				rs.getLong("crew_id"),
				rs.getLong("owner_user_id"),
				rs.getString("name"),
				rs.getString("description"),
				rs.getString("visibility"),
				rs.getString("join_policy"),
				rs.getString("region"),
				rs.getString("profile_image_url"),
				rs.getInt("member_count"),
				rs.getString("viewer_role"),
				rs.getString("viewer_status"),
				timestamp(rs.getTimestamp("created_at")),
				timestamp(rs.getTimestamp("updated_at"))
			), viewerUserId, crewId);
		return rows.stream().findFirst();
	}

	public List<CrewResponse> listCrews(long viewerUserId, boolean mine, String region, String keyword, int limit) {
		StringBuilder sql = new StringBuilder("""
				SELECT c.crew_id, c.owner_user_id, c.name, c.description, c.visibility, c.join_policy,
					c.region, c.profile_image_url, c.member_count, c.created_at, c.updated_at,
					viewer.role AS viewer_role, viewer.status AS viewer_status
				FROM crews c
				LEFT JOIN crew_members viewer ON viewer.crew_id = c.crew_id AND viewer.user_id = ?
				WHERE (c.visibility = 'PUBLIC' OR viewer.status = 'ACCEPTED')
				""");
		List<Object> args = new ArrayList<>();
		args.add(viewerUserId);
		if (mine) {
			sql.append(" AND viewer.status = 'ACCEPTED'");
		}
		if (region != null) {
			sql.append(" AND c.region = ?");
			args.add(region);
		}
		if (keyword != null) {
			sql.append(" AND (c.name LIKE ? OR c.description LIKE ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
		}
		sql.append(" ORDER BY c.created_at DESC, c.crew_id DESC LIMIT ?");
		args.add(limit);
		return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new CrewResponse(
				rs.getLong("crew_id"),
				rs.getLong("owner_user_id"),
				rs.getString("name"),
				rs.getString("description"),
				rs.getString("visibility"),
				rs.getString("join_policy"),
				rs.getString("region"),
				rs.getString("profile_image_url"),
				rs.getInt("member_count"),
				rs.getString("viewer_role"),
				rs.getString("viewer_status"),
				timestamp(rs.getTimestamp("created_at")),
				timestamp(rs.getTimestamp("updated_at"))
		), args.toArray());
	}

	public Optional<CrewMembership> findMembership(long crewId, long userId) {
		List<CrewMembership> rows = jdbcTemplate.query("""
				SELECT crew_id, user_id, role, status
				FROM crew_members
				WHERE crew_id = ? AND user_id = ?
				""", (rs, rowNum) -> new CrewMembership(
				rs.getLong("crew_id"),
				rs.getLong("user_id"),
				rs.getString("role"),
				rs.getString("status")
		), crewId, userId);
		return rows.stream().findFirst();
	}

	public void saveMembership(long crewId, long userId, String status) {
		jdbcTemplate.update("""
				INSERT INTO crew_members
					(crew_id, user_id, role, status, requested_at, responded_at, joined_at)
				VALUES (?, ?, 'MEMBER', ?, CURRENT_TIMESTAMP,
					CASE WHEN ? IN ('ACCEPTED', 'REJECTED') THEN CURRENT_TIMESTAMP ELSE NULL END,
					CASE WHEN ? = 'ACCEPTED' THEN CURRENT_TIMESTAMP ELSE NULL END)
				ON DUPLICATE KEY UPDATE
					status = VALUES(status),
					requested_at = VALUES(requested_at),
					responded_at = VALUES(responded_at),
					joined_at = VALUES(joined_at),
					role = IF(role = 'OWNER', role, 'MEMBER')
				""", crewId, userId, status, status, status);
	}

	public int updateMemberStatus(long crewId, long userId, String status) {
		return jdbcTemplate.update("""
				UPDATE crew_members
				SET status = ?,
					responded_at = CURRENT_TIMESTAMP,
					joined_at = CASE WHEN ? = 'ACCEPTED' THEN COALESCE(joined_at, CURRENT_TIMESTAMP) ELSE joined_at END
				WHERE crew_id = ? AND user_id = ?
				""", status, status, crewId, userId);
	}

	public int leaveCrew(long crewId, long userId) {
		return jdbcTemplate.update("""
				UPDATE crew_members
				SET status = 'LEFT', responded_at = CURRENT_TIMESTAMP
				WHERE crew_id = ? AND user_id = ? AND role <> 'OWNER'
				""", crewId, userId);
	}

	public int adjustMemberCount(long crewId, int delta) {
		return jdbcTemplate.update("UPDATE crews SET member_count = GREATEST(0, member_count + ?) WHERE crew_id = ?", delta, crewId);
	}

	public int refreshMemberCount(long crewId) {
		return jdbcTemplate.update("""
				UPDATE crews
				SET member_count = (
					SELECT COUNT(*)
					FROM crew_members cm
					JOIN users u ON u.user_id = cm.user_id
					WHERE cm.crew_id = crews.crew_id AND cm.status = 'ACCEPTED' AND u.deleted_at IS NULL
				)
				WHERE crew_id = ?
				""", crewId);
	}

	public List<Long> deactivateUserCrewState(long userId) {
		List<Long> affectedCrewIds = jdbcTemplate.query("""
				SELECT DISTINCT crew_id
				FROM crew_members
				WHERE user_id = ? AND status IN ('REQUESTED', 'ACCEPTED', 'INVITED')
				""", (rs, rowNum) -> rs.getLong("crew_id"), userId);
		jdbcTemplate.update("""
				UPDATE crew_members
				SET status = 'LEFT', responded_at = CURRENT_TIMESTAMP
				WHERE user_id = ? AND status IN ('REQUESTED', 'ACCEPTED', 'INVITED')
				""", userId);
		jdbcTemplate.update("""
				UPDATE crew_event_participants
				SET status = 'CANCELLED', responded_at = CURRENT_TIMESTAMP
				WHERE user_id = ? AND status IN ('REQUESTED', 'ACCEPTED')
				""", userId);
		jdbcTemplate.update("""
				UPDATE instant_crew_participants
				SET status = 'CANCELLED', responded_at = CURRENT_TIMESTAMP, joined_at = NULL
				WHERE user_id = ? AND status IN ('REQUESTED', 'ACCEPTED')
				""", userId);
		for (Long crewId : affectedCrewIds) {
			refreshMemberCount(crewId);
		}
		return affectedCrewIds;
	}

	public List<CrewMemberResponse> listAcceptedMembers(long crewId) {
		return jdbcTemplate.query("""
				SELECT cm.crew_id, cm.user_id, COALESCE(NULLIF(u.community_profile_name, ''), u.name) AS nickname,
					u.profile_photo AS profile_image_url, cm.role, cm.status, cm.joined_at
				FROM crew_members cm
				JOIN users u ON u.user_id = cm.user_id
				WHERE cm.crew_id = ? AND cm.status = 'ACCEPTED' AND u.deleted_at IS NULL
				ORDER BY FIELD(cm.role, 'OWNER', 'ADMIN', 'MEMBER'), cm.joined_at ASC, cm.user_id ASC
				""", (rs, rowNum) -> new CrewMemberResponse(
				rs.getLong("crew_id"),
				rs.getLong("user_id"),
				rs.getString("nickname"),
				rs.getString("profile_image_url"),
				rs.getString("role"),
				rs.getString("status"),
				timestamp(rs.getTimestamp("joined_at"))
		), crewId);
	}

	public List<CrewMemberRequestResponse> listMemberRequests(long crewId) {
		return jdbcTemplate.query("""
				SELECT cm.crew_id, cm.user_id, COALESCE(NULLIF(u.community_profile_name, ''), u.name) AS nickname,
					u.profile_photo AS profile_image_url, cm.role, cm.status, cm.requested_at
				FROM crew_members cm
				JOIN users u ON u.user_id = cm.user_id
				WHERE cm.crew_id = ? AND cm.status = 'REQUESTED' AND u.deleted_at IS NULL
				ORDER BY cm.requested_at ASC, cm.user_id ASC
				""", (rs, rowNum) -> new CrewMemberRequestResponse(
			rs.getLong("crew_id"),
			rs.getLong("user_id"),
			rs.getString("nickname"),
			rs.getString("profile_image_url"),
			rs.getString("role"),
			rs.getString("status"),
			timestamp(rs.getTimestamp("requested_at"))
		), crewId);
	}

	public long createEvent(long crewId, long hostUserId, String title, String description, String eventType,
			LocalDateTime startsAt, LocalDateTime endsAt, String locationLabel, String meetingPlace, Integer capacity) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO crew_events
						(crew_id, host_user_id, title, description, event_type, starts_at, ends_at, location_label, meeting_place, capacity)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, crewId);
			ps.setLong(2, hostUserId);
			ps.setString(3, title);
			ps.setString(4, description);
			ps.setString(5, eventType);
			ps.setObject(6, startsAt);
			ps.setObject(7, endsAt);
			ps.setString(8, locationLabel);
			ps.setString(9, meetingPlace);
			ps.setObject(10, capacity);
			return ps;
		}, keyHolder);
		return generatedId(keyHolder, "크루 일정 ID를 생성하지 못했습니다.");
	}

	public Optional<CrewEventRow> findEvent(long crewId, long eventId) {
		List<CrewEventRow> rows = jdbcTemplate.query("""
				SELECT crew_event_id, crew_id, status, capacity
				FROM crew_events
				WHERE crew_id = ? AND crew_event_id = ?
				""", (rs, rowNum) -> new CrewEventRow(
				rs.getLong("crew_event_id"),
				rs.getLong("crew_id"),
				rs.getString("status"),
				nullableInt(rs.getObject("capacity"))
		), crewId, eventId);
		return rows.stream().findFirst();
	}

	public Optional<CrewEventRow> findEventForUpdate(long crewId, long eventId) {
		List<CrewEventRow> rows = jdbcTemplate.query("""
				SELECT crew_event_id, crew_id, status, capacity
				FROM crew_events
				WHERE crew_id = ? AND crew_event_id = ?
				FOR UPDATE
				""", (rs, rowNum) -> new CrewEventRow(
			rs.getLong("crew_event_id"),
			rs.getLong("crew_id"),
			rs.getString("status"),
			nullableInt(rs.getObject("capacity"))
		), crewId, eventId);
		return rows.stream().findFirst();
	}

	public Optional<CrewEventResponse> findEventResponse(long crewId, long eventId) {
		List<CrewEventResponse> rows = listEventsBySql(" AND ce.crew_event_id = ?", List.of(crewId, eventId));
		return rows.stream().findFirst();
	}

	public int updateEvent(long crewId, long eventId, String title, String description, String eventType,
			LocalDateTime startsAt, LocalDateTime endsAt, String locationLabel, String meetingPlace, Integer capacity) {
		return jdbcTemplate.update("""
				UPDATE crew_events
				SET title = COALESCE(?, title),
					description = COALESCE(?, description),
					event_type = COALESCE(?, event_type),
					starts_at = COALESCE(?, starts_at),
					ends_at = COALESCE(?, ends_at),
					location_label = COALESCE(?, location_label),
					meeting_place = COALESCE(?, meeting_place),
					capacity = COALESCE(?, capacity)
				WHERE crew_id = ? AND crew_event_id = ?
				""", title, description, eventType, startsAt, endsAt, locationLabel, meetingPlace, capacity, crewId, eventId);
	}

	public int updateEventStatus(long crewId, long eventId, String status) {
		return jdbcTemplate.update(
				"UPDATE crew_events SET status = ? WHERE crew_id = ? AND crew_event_id = ?",
				status, crewId, eventId
		);
	}

	public int deleteEvent(long crewId, long eventId) {
		return jdbcTemplate.update("DELETE FROM crew_events WHERE crew_id = ? AND crew_event_id = ?", crewId, eventId);
	}

	public List<CrewEventResponse> listEvents(long crewId) {
		return listEventsBySql("", List.of(crewId));
	}

	private List<CrewEventResponse> listEventsBySql(String extraPredicate, List<Object> args) {
		String sql = """
				SELECT ce.crew_event_id, ce.crew_id, ce.host_user_id, ce.title, ce.description, ce.event_type,
					ce.status, ce.starts_at, ce.ends_at, ce.location_label, ce.meeting_place, ce.capacity,
					(SELECT COUNT(*) FROM crew_event_participants cep
					 JOIN users u ON u.user_id = cep.user_id
					 WHERE cep.crew_event_id = ce.crew_event_id AND cep.status IN ('ACCEPTED', 'ATTENDED') AND u.deleted_at IS NULL) AS participant_count
				FROM crew_events ce
				WHERE ce.crew_id = ?
				""" + extraPredicate + " ORDER BY ce.starts_at ASC, ce.crew_event_id ASC";
		return jdbcTemplate.query(sql, (rs, rowNum) -> new CrewEventResponse(
				rs.getLong("crew_event_id"),
				rs.getLong("crew_id"),
				rs.getLong("host_user_id"),
				rs.getString("title"),
				rs.getString("description"),
				rs.getString("event_type"),
				rs.getString("status"),
				timestamp(rs.getTimestamp("starts_at")),
				timestamp(rs.getTimestamp("ends_at")),
				rs.getString("location_label"),
				rs.getString("meeting_place"),
				nullableInt(rs.getObject("capacity")),
				rs.getInt("participant_count")
		), args.toArray());
	}

	public int acceptedEventParticipantCount(long eventId) {
		return acceptedEventParticipantCountExcluding(eventId, null);
	}

	public int acceptedEventParticipantCountExcluding(long eventId, Long excludedUserId) {
		String excludedPredicate = excludedUserId == null ? "" : " AND cep.user_id <> ?";
		List<Object> args = new ArrayList<>();
		args.add(eventId);
		if (excludedUserId != null) {
			args.add(excludedUserId);
		}
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM crew_event_participants cep
				JOIN users u ON u.user_id = cep.user_id
				WHERE cep.crew_event_id = ? AND cep.status IN ('ACCEPTED', 'ATTENDED') AND u.deleted_at IS NULL
				""" + excludedPredicate, Integer.class, args.toArray());
		return count == null ? 0 : count;
	}

	public void upsertEventParticipation(long eventId, long userId, String status) {
		jdbcTemplate.update("""
				INSERT INTO crew_event_participants
					(crew_event_id, user_id, status, requested_at, responded_at)
				VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				ON DUPLICATE KEY UPDATE
					status = VALUES(status),
					responded_at = CURRENT_TIMESTAMP
				""", eventId, userId, status);
	}

	public void markEventAttendance(long eventId, long userId, String status, Long runningRecordId) {
		jdbcTemplate.update("""
				INSERT INTO crew_event_participants
					(crew_event_id, user_id, status, running_record_id, requested_at, responded_at, attended_at)
				VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CASE WHEN ? = 'ATTENDED' THEN CURRENT_TIMESTAMP ELSE NULL END)
				ON DUPLICATE KEY UPDATE
					status = VALUES(status),
					running_record_id = VALUES(running_record_id),
					responded_at = CURRENT_TIMESTAMP,
					attended_at = VALUES(attended_at)
				""", eventId, userId, status, runningRecordId, status);
	}

	public Map<Long, Long> attendanceCounts(long crewId, Collection<Long> userIds, LocalDate from, LocalDate to) {
		if (userIds == null || userIds.isEmpty()) {
			return Map.of();
		}
		List<Long> targets = userIds.stream().distinct().toList();
		String placeholders = String.join(",", java.util.Collections.nCopies(targets.size(), "?"));
		StringBuilder sql = new StringBuilder("""
				SELECT cep.user_id, COUNT(*) AS attendance_count
				FROM crew_event_participants cep
				JOIN crew_events ce ON ce.crew_event_id = cep.crew_event_id
				WHERE ce.crew_id = ? AND cep.status = 'ATTENDED' AND cep.user_id IN (%s)
				""".formatted(placeholders));
		List<Object> args = new ArrayList<>();
		args.add(crewId);
		args.addAll(targets);
		if (from != null) {
			sql.append(" AND ce.starts_at >= ?");
			args.add(from.atStartOfDay());
		}
		if (to != null) {
			sql.append(" AND ce.starts_at < ?");
			args.add(to.plusDays(1).atStartOfDay());
		}
		sql.append(" GROUP BY cep.user_id");
		Map<Long, Long> counts = new LinkedHashMap<>();
		jdbcTemplate.query(sql.toString(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> counts.put(rs.getLong("user_id"), rs.getLong("attendance_count")), args.toArray());
		for (Long userId : targets) {
			counts.putIfAbsent(userId, 0L);
		}
		return counts;
	}

	public long createInstantCrew(Long crewId, long hostUserId, String title, String description, String region,
			String locationLabel, String meetingPlace, LocalDateTime startsAt, LocalDateTime recruitUntil, int capacity) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO instant_crews
						(crew_id, host_user_id, title, description, region, location_label, meeting_place_private, starts_at, recruit_until, capacity)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setObject(1, crewId);
			ps.setLong(2, hostUserId);
			ps.setString(3, title);
			ps.setString(4, description);
			ps.setString(5, region);
			ps.setString(6, locationLabel);
			ps.setString(7, meetingPlace);
			ps.setObject(8, startsAt);
			ps.setObject(9, recruitUntil);
			ps.setInt(10, capacity);
			return ps;
		}, keyHolder);
		return generatedId(keyHolder, "번개 크루 ID를 생성하지 못했습니다.");
	}

	public List<InstantCrewResponse> listInstantCrews(long viewerUserId, String region, int limit) {
		StringBuilder sql = new StringBuilder("""
				SELECT ic.instant_crew_id, ic.crew_id, ic.host_user_id, ic.title, ic.description, ic.status,
					ic.region, ic.location_label, ic.meeting_place_private, ic.starts_at, ic.recruit_until, ic.capacity,
					ic.created_at, viewer.status AS viewer_status,
					(SELECT COUNT(*) FROM instant_crew_participants p
					 JOIN users u ON u.user_id = p.user_id
					 WHERE p.instant_crew_id = ic.instant_crew_id AND p.status = 'ACCEPTED' AND u.deleted_at IS NULL) AS participant_count
				FROM instant_crews ic
				LEFT JOIN instant_crew_participants viewer ON viewer.instant_crew_id = ic.instant_crew_id AND viewer.user_id = ?
				WHERE ic.status = 'OPEN' AND ic.recruit_until >= CURRENT_TIMESTAMP
				""");
		List<Object> args = new ArrayList<>();
		args.add(viewerUserId);
		if (region != null) {
			sql.append(" AND ic.region = ?");
			args.add(region);
		}
		sql.append(" ORDER BY ic.starts_at ASC, ic.instant_crew_id ASC LIMIT ?");
		args.add(limit);
		return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> instantCrewResponse(rs, true), args.toArray());
	}

	public Optional<InstantCrewResponse> findInstantCrew(long instantCrewId, long viewerUserId) {
		return findInstantCrewBySql("""
				SELECT ic.instant_crew_id, ic.crew_id, ic.host_user_id, ic.title, ic.description, ic.status,
					ic.region, ic.location_label, ic.meeting_place_private, ic.starts_at, ic.recruit_until, ic.capacity,
					ic.created_at, viewer.status AS viewer_status,
					(SELECT COUNT(*) FROM instant_crew_participants p
					 JOIN users u ON u.user_id = p.user_id
					 WHERE p.instant_crew_id = ic.instant_crew_id AND p.status = 'ACCEPTED' AND u.deleted_at IS NULL) AS participant_count
				FROM instant_crews ic
				LEFT JOIN instant_crew_participants viewer ON viewer.instant_crew_id = ic.instant_crew_id AND viewer.user_id = ?
				WHERE ic.instant_crew_id = ?
				""", instantCrewId, viewerUserId);
	}

	public Optional<InstantCrewResponse> findInstantCrewForUpdate(long instantCrewId, long viewerUserId) {
		return findInstantCrewBySql("""
				SELECT ic.instant_crew_id, ic.crew_id, ic.host_user_id, ic.title, ic.description, ic.status,
					ic.region, ic.location_label, ic.meeting_place_private, ic.starts_at, ic.recruit_until, ic.capacity,
					ic.created_at, viewer.status AS viewer_status,
					(SELECT COUNT(*) FROM instant_crew_participants p
					 JOIN users u ON u.user_id = p.user_id
					 WHERE p.instant_crew_id = ic.instant_crew_id AND p.status = 'ACCEPTED' AND u.deleted_at IS NULL) AS participant_count
				FROM instant_crews ic
				LEFT JOIN instant_crew_participants viewer ON viewer.instant_crew_id = ic.instant_crew_id AND viewer.user_id = ?
				WHERE ic.instant_crew_id = ?
				FOR UPDATE
				""", instantCrewId, viewerUserId);
	}

	private Optional<InstantCrewResponse> findInstantCrewBySql(String sql, long instantCrewId, long viewerUserId) {
		List<InstantCrewResponse> rows = jdbcTemplate.query(sql, (rs, rowNum) -> instantCrewResponse(rs, false), viewerUserId, instantCrewId);
		return rows.stream().findFirst();
	}

	public Optional<InstantParticipant> findInstantParticipant(long instantCrewId, long userId) {
		List<InstantParticipant> rows = jdbcTemplate.query("""
				SELECT instant_crew_id, user_id, status
				FROM instant_crew_participants
				WHERE instant_crew_id = ? AND user_id = ?
				""", (rs, rowNum) -> new InstantParticipant(
				rs.getLong("instant_crew_id"),
				rs.getLong("user_id"),
				rs.getString("status")
		), instantCrewId, userId);
		return rows.stream().findFirst();
	}

	public void saveInstantParticipant(long instantCrewId, long userId, String status) {
		jdbcTemplate.update("""
				INSERT INTO instant_crew_participants
					(instant_crew_id, user_id, status, requested_at, responded_at, joined_at)
				VALUES (?, ?, ?, CURRENT_TIMESTAMP,
					CASE WHEN ? IN ('ACCEPTED', 'REJECTED') THEN CURRENT_TIMESTAMP ELSE NULL END,
					CASE WHEN ? = 'ACCEPTED' THEN CURRENT_TIMESTAMP ELSE NULL END)
				ON DUPLICATE KEY UPDATE
					status = VALUES(status),
					requested_at = VALUES(requested_at),
					responded_at = VALUES(responded_at),
					joined_at = VALUES(joined_at)
				""", instantCrewId, userId, status, status, status);
	}

	public int updateInstantParticipantStatus(long instantCrewId, long userId, String status) {
		return jdbcTemplate.update("""
				UPDATE instant_crew_participants
				SET status = ?,
					responded_at = CURRENT_TIMESTAMP,
					joined_at = CASE WHEN ? = 'ACCEPTED' THEN COALESCE(joined_at, CURRENT_TIMESTAMP) ELSE joined_at END
				WHERE instant_crew_id = ? AND user_id = ?
				""", status, status, instantCrewId, userId);
	}

	public int cancelInstantParticipant(long instantCrewId, long userId) {
		return jdbcTemplate.update("""
				UPDATE instant_crew_participants
				SET status = 'CANCELLED',
					responded_at = CURRENT_TIMESTAMP,
					joined_at = NULL
				WHERE instant_crew_id = ? AND user_id = ?
				""", instantCrewId, userId);
	}

	public int acceptedInstantParticipantCount(long instantCrewId) {
		return acceptedInstantParticipantCountExcluding(instantCrewId, null);
	}

	public int acceptedInstantParticipantCountExcluding(long instantCrewId, Long excludedUserId) {
		String excludedPredicate = excludedUserId == null ? "" : " AND p.user_id <> ?";
		List<Object> args = new ArrayList<>();
		args.add(instantCrewId);
		if (excludedUserId != null) {
			args.add(excludedUserId);
		}
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM instant_crew_participants p
				JOIN users u ON u.user_id = p.user_id
				WHERE p.instant_crew_id = ? AND p.status = 'ACCEPTED' AND u.deleted_at IS NULL
				""" + excludedPredicate, Integer.class, args.toArray());
		return count == null ? 0 : count;
	}

	public int updateInstantCrewStatus(long instantCrewId, String status) {
		return jdbcTemplate.update("UPDATE instant_crews SET status = ? WHERE instant_crew_id = ?", status, instantCrewId);
	}

	public List<InstantCrewParticipantRequestResponse> listInstantParticipantRequests(long instantCrewId) {
		return jdbcTemplate.query("""
				SELECT p.instant_crew_id, p.user_id, COALESCE(NULLIF(u.community_profile_name, ''), u.name) AS nickname,
					u.profile_photo AS profile_image_url, p.status, p.requested_at
				FROM instant_crew_participants p
				JOIN users u ON u.user_id = p.user_id
				WHERE p.instant_crew_id = ? AND p.status = 'REQUESTED' AND u.deleted_at IS NULL
				ORDER BY p.requested_at ASC, p.user_id ASC
				""", (rs, rowNum) -> new InstantCrewParticipantRequestResponse(
			rs.getLong("instant_crew_id"),
			rs.getLong("user_id"),
			rs.getString("nickname"),
			rs.getString("profile_image_url"),
			rs.getString("status"),
			timestamp(rs.getTimestamp("requested_at"))
		), instantCrewId);
	}

	public List<InstantCrewParticipantResponse> listAcceptedInstantParticipants(long instantCrewId) {
		return jdbcTemplate.query("""
				SELECT p.user_id, COALESCE(NULLIF(u.community_profile_name, ''), u.name) AS nickname,
					u.profile_photo AS profile_image_url, p.status, p.joined_at
				FROM instant_crew_participants p
				JOIN users u ON u.user_id = p.user_id
				WHERE p.instant_crew_id = ? AND p.status = 'ACCEPTED' AND u.deleted_at IS NULL
				ORDER BY p.joined_at ASC, p.user_id ASC
				""", (rs, rowNum) -> new InstantCrewParticipantResponse(
			rs.getLong("user_id"),
			rs.getString("nickname"),
			rs.getString("profile_image_url"),
			rs.getString("status"),
			timestamp(rs.getTimestamp("joined_at"))
		), instantCrewId);
	}

	public long insertCrewChatMessage(long crewId, long senderUserId, String messageText) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO crew_chat_messages (crew_id, sender_user_id, message_text)
					VALUES (?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, crewId);
			ps.setLong(2, senderUserId);
			ps.setString(3, messageText);
			return ps;
		}, keyHolder);
		return generatedId(keyHolder, "채팅 메시지 ID를 생성하지 못했습니다.");
	}

	public long insertInstantChatMessage(long instantCrewId, long senderUserId, String messageText) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO crew_chat_messages (instant_crew_id, sender_user_id, message_text)
					VALUES (?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, instantCrewId);
			ps.setLong(2, senderUserId);
			ps.setString(3, messageText);
			return ps;
		}, keyHolder);
		return generatedId(keyHolder, "채팅 메시지 ID를 생성하지 못했습니다.");
	}

	public Optional<CrewChatMessageResponse> findChatMessage(long messageId) {
		List<CrewChatMessageResponse> rows = jdbcTemplate.query(chatMessageSelect() + " WHERE m.message_id = ?", this::chatMessageResponse, messageId);
		return rows.stream().findFirst();
	}

	public List<CrewChatMessageResponse> listCrewMessages(long crewId, Long beforeMessageId, int limit) {
		List<Object> args = new ArrayList<>();
		args.add(crewId);
		StringBuilder sql = new StringBuilder(chatMessageSelect() + " WHERE m.crew_id = ?");
		if (beforeMessageId != null) {
			sql.append(" AND m.message_id < ?");
			args.add(beforeMessageId);
		}
		sql.append(" ORDER BY m.message_id DESC LIMIT ?");
		args.add(limit);
		return jdbcTemplate.query(sql.toString(), this::chatMessageResponse, args.toArray());
	}

	public List<CrewChatMessageResponse> listInstantMessages(long instantCrewId, Long beforeMessageId, int limit) {
		List<Object> args = new ArrayList<>();
		args.add(instantCrewId);
		StringBuilder sql = new StringBuilder(chatMessageSelect() + " WHERE m.instant_crew_id = ?");
		if (beforeMessageId != null) {
			sql.append(" AND m.message_id < ?");
			args.add(beforeMessageId);
		}
		sql.append(" ORDER BY m.message_id DESC LIMIT ?");
		args.add(limit);
		return jdbcTemplate.query(sql.toString(), this::chatMessageResponse, args.toArray());
	}

	private String chatMessageSelect() {
		return """
				SELECT m.message_id, m.crew_id, m.instant_crew_id, m.sender_user_id,
					CASE WHEN u.deleted_at IS NULL THEN COALESCE(NULLIF(u.community_profile_name, ''), u.name) ELSE '탈퇴한 사용자' END AS nickname,
					CASE WHEN u.deleted_at IS NULL THEN u.profile_photo ELSE NULL END AS profile_image_url, m.message_type, m.message_text, m.created_at
				FROM crew_chat_messages m
				JOIN users u ON u.user_id = m.sender_user_id
				""";
	}

	private CrewChatMessageResponse chatMessageResponse(ResultSet rs, int rowNum) throws SQLException {
		return new CrewChatMessageResponse(
				rs.getLong("message_id"),
				nullableLong(rs.getObject("crew_id")),
				nullableLong(rs.getObject("instant_crew_id")),
				rs.getLong("sender_user_id"),
				rs.getString("nickname"),
				rs.getString("profile_image_url"),
				rs.getString("message_type"),
				rs.getString("message_text"),
				timestamp(rs.getTimestamp("created_at"))
		);
	}

	private InstantCrewResponse instantCrewResponse(ResultSet rs, boolean hideMeetingPlace) throws SQLException {
		return new InstantCrewResponse(
				rs.getLong("instant_crew_id"),
				nullableLong(rs.getObject("crew_id")),
				rs.getLong("host_user_id"),
				rs.getString("title"),
				rs.getString("description"),
				rs.getString("status"),
				rs.getString("region"),
				rs.getString("location_label"),
				hideMeetingPlace ? null : rs.getString("meeting_place_private"),
				timestamp(rs.getTimestamp("starts_at")),
				timestamp(rs.getTimestamp("recruit_until")),
				rs.getInt("capacity"),
				rs.getInt("participant_count"),
				rs.getString("viewer_status"),
				timestamp(rs.getTimestamp("created_at"))
		);
	}

	private long generatedId(KeyHolder keyHolder, String message) {
		Number generatedId = keyHolder.getKey();
		if (generatedId == null) {
			throw new IllegalStateException(message);
		}
		return generatedId.longValue();
	}

	private static String timestamp(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toLocalDateTime().format(RESPONSE_TIME_FORMATTER);
	}

	private static Long nullableLong(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}

	private static Integer nullableInt(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}

	public record CrewMembership(long crewId, long userId, String role, String status) {}

	public record CrewEventRow(long crewEventId, long crewId, String status, Integer capacity) {}

	public record InstantParticipant(long instantCrewId, long userId, String status) {}
}
