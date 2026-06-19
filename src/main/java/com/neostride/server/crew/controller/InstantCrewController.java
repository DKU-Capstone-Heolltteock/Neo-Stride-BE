package com.neostride.server.crew.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.crew.dto.InstantCrewApplicationResponse;
import com.neostride.server.crew.dto.InstantCrewParticipantRequestResponse;
import com.neostride.server.crew.dto.InstantCrewParticipantResponse;
import com.neostride.server.crew.dto.InstantCrewRequest;
import com.neostride.server.crew.dto.InstantCrewResponse;
import com.neostride.server.crew.dto.InstantCrewStatusRequest;
import com.neostride.server.crew.service.CrewService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instant-crews")
public class InstantCrewController {
	private final CrewService crewService;
	private final AuthenticatedUserService authenticatedUserService;

	public InstantCrewController(CrewService crewService, AuthenticatedUserService authenticatedUserService) {
		this.crewService = crewService;
		this.authenticatedUserService = authenticatedUserService;
	}

	@PostMapping
	public InstantCrewResponse createInstantCrew(
			@RequestHeader("Authorization") String authorization,
			@RequestBody InstantCrewRequest request
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.createInstantCrew(userId, request);
	}

	@GetMapping
	public List<InstantCrewResponse> listInstantCrews(
			@RequestHeader("Authorization") String authorization,
			@RequestParam(name = "region", required = false) String region,
			@RequestParam(name = "limit", required = false) Integer limit
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.listInstantCrews(userId, region, limit);
	}

	@GetMapping("/{instantCrewId}")
	public InstantCrewResponse getInstantCrew(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long instantCrewId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.getInstantCrew(userId, instantCrewId);
	}

	@PostMapping("/{instantCrewId}/apply")
	public InstantCrewApplicationResponse applyInstantCrew(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long instantCrewId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.applyInstantCrew(userId, instantCrewId);
	}

	@PostMapping("/{instantCrewId}/participants/{userId}/approve")
	public InstantCrewApplicationResponse approveParticipant(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long instantCrewId,
			@PathVariable long userId
	) {
		long actorUserId = authenticatedUserService.requireUserId(authorization);
		return crewService.approveInstantParticipant(actorUserId, instantCrewId, userId);
	}

	@GetMapping("/{instantCrewId}/participants/requests")
	public List<InstantCrewParticipantRequestResponse> listParticipantRequests(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long instantCrewId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.listInstantParticipantRequests(userId, instantCrewId);
	}

	@GetMapping("/{instantCrewId}/participants")
	public List<InstantCrewParticipantResponse> listParticipants(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long instantCrewId
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.listInstantParticipants(userId, instantCrewId);
	}

	@PostMapping("/{instantCrewId}/participants/{userId}/reject")
	public InstantCrewApplicationResponse rejectParticipant(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long instantCrewId,
			@PathVariable long userId
	) {
		long actorUserId = authenticatedUserService.requireUserId(authorization);
		return crewService.rejectInstantParticipant(actorUserId, instantCrewId, userId);
	}

	@PostMapping("/{instantCrewId}/status")
	public InstantCrewResponse updateStatus(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long instantCrewId,
			@RequestBody InstantCrewStatusRequest request
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.updateInstantStatus(userId, instantCrewId, request);
	}
}
