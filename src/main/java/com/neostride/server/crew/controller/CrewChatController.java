package com.neostride.server.crew.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.crew.dto.CrewChatMessageRequest;
import com.neostride.server.crew.dto.CrewChatMessageResponse;
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
@RequestMapping("/api/crew-chat")
public class CrewChatController {
	private final CrewService crewService;
	private final AuthenticatedUserService authenticatedUserService;

	public CrewChatController(CrewService crewService, AuthenticatedUserService authenticatedUserService) {
		this.crewService = crewService;
		this.authenticatedUserService = authenticatedUserService;
	}

	@PostMapping("/crews/{crewId}/messages")
	public CrewChatMessageResponse sendCrewMessage(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId,
			@RequestBody CrewChatMessageRequest request
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.sendCrewMessage(userId, crewId, request);
	}

	@GetMapping("/crews/{crewId}/messages")
	public List<CrewChatMessageResponse> listCrewMessages(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long crewId,
			@RequestParam(name = "before_message_id", required = false) Long beforeMessageId,
			@RequestParam(name = "limit", required = false) Integer limit
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.listCrewMessages(userId, crewId, beforeMessageId, limit);
	}

	@PostMapping("/instant-crews/{instantCrewId}/messages")
	public CrewChatMessageResponse sendInstantMessage(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long instantCrewId,
			@RequestBody CrewChatMessageRequest request
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.sendInstantMessage(userId, instantCrewId, request);
	}

	@GetMapping("/instant-crews/{instantCrewId}/messages")
	public List<CrewChatMessageResponse> listInstantMessages(
			@RequestHeader("Authorization") String authorization,
			@PathVariable long instantCrewId,
			@RequestParam(name = "before_message_id", required = false) Long beforeMessageId,
			@RequestParam(name = "limit", required = false) Integer limit
	) {
		long userId = authenticatedUserService.requireUserId(authorization);
		return crewService.listInstantMessages(userId, instantCrewId, beforeMessageId, limit);
	}
}
