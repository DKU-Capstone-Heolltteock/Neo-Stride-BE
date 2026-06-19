package com.neostride.server.crew.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.crew.dto.CrewCreateRequest;
import com.neostride.server.crew.dto.CrewEventAttendanceRequest;
import com.neostride.server.crew.dto.CrewEventRequest;
import com.neostride.server.crew.dto.CrewEventResponse;
import com.neostride.server.crew.dto.CrewJoinResponse;
import com.neostride.server.crew.dto.CrewMemberRequestResponse;
import com.neostride.server.crew.dto.CrewMemberResponse;
import com.neostride.server.crew.dto.CrewRankingResponse;
import com.neostride.server.crew.dto.CrewResponse;
import com.neostride.server.crew.dto.CrewUpdateRequest;
import com.neostride.server.crew.service.CrewService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crews")
public class CrewController {
	private final CrewService crewService;
	private final AuthenticatedUserService authenticatedUserService;

	public CrewController(CrewService crewService, AuthenticatedUserService authenticatedUserService) {
		this.crewService = crewService;
		this.authenticatedUserService = authenticatedUserService;
	}

	@PostMapping
	public CrewResponse createCrew(
			@RequestHeader("Authorization") String authorization,
			@RequestBody CrewCreateRequest request
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.createCrew(userId, request);
	}

	@GetMapping
	public List<CrewResponse> listCrews(
			@RequestHeader("Authorization") String authorization,
			@RequestParam(name = "mine", defaultValue = "false") boolean mine,
			@RequestParam(name = "region", required = false) String region,
			@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "limit", required = false) Integer limit
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.listCrews(userId, mine, region, keyword, limit);
	}

	@GetMapping("/{crewId}")
	public CrewResponse getCrew(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.getCrew(userId, crewId);
	}

	@PatchMapping("/{crewId}")
	public CrewResponse updateCrew(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId,
			@RequestBody CrewUpdateRequest request
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.updateCrew(userId, crewId, request);
	}

	@PostMapping("/{crewId}/join")
	public CrewJoinResponse joinCrew(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.joinCrew(userId, crewId);
	}

	@PostMapping("/{crewId}/members/{userId}/approve")
	public CrewJoinResponse approveMember(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId,
			@PathVariable long userId
	) {
		long actorUserId = authenticatedUserService.requireUserId(authorization);
		return crewService.approveCrewMember(actorUserId, crewId, userId);
	}

	@PostMapping("/{crewId}/members/{userId}/reject")
	public CrewJoinResponse rejectMember(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId,
			@PathVariable long userId
	) {
		long actorUserId = authenticatedUserService.requireUserId(authorization);
		return crewService.rejectCrewMember(actorUserId, crewId, userId);
	}

	@DeleteMapping("/{crewId}/members/me")
	public CrewJoinResponse leaveCrew(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.leaveCrew(userId, crewId);
	}

	@GetMapping("/{crewId}/members")
	public List<CrewMemberResponse> listMembers(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.listMembers(userId, crewId);
	}

	@GetMapping("/{crewId}/members/requests")
	public List<CrewMemberRequestResponse> listMemberRequests(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.listMemberRequests(userId, crewId);
	}

	@PostMapping("/{crewId}/events")
	public CrewEventResponse createEvent(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId,
			@RequestBody CrewEventRequest request
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.createEvent(userId, crewId, request);
	}

	@GetMapping("/{crewId}/events")
	public List<CrewEventResponse> listEvents(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.listEvents(userId, crewId);
	}

	@PostMapping("/{crewId}/events/{eventId}/join")
	public CrewJoinResponse joinEvent(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId,
			@PathVariable long eventId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.joinEvent(userId, crewId, eventId);
	}

	@PostMapping("/{crewId}/events/{eventId}/attendance")
	public CrewEventResponse markAttendance(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId,
			@PathVariable long eventId,
			@RequestBody CrewEventAttendanceRequest request
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.markEventAttendance(userId, crewId, eventId, request);
	}

	@GetMapping("/{crewId}/rankings")
	public CrewRankingResponse rankings(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId,
			@RequestParam(name = "period", required = false) String period
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.rankings(userId, crewId, period);
	}
}
