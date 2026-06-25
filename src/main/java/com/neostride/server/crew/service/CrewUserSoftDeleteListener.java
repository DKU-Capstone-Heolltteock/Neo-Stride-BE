package com.neostride.server.crew.service;

import com.neostride.server.crew.repository.CrewRepository;
import com.neostride.server.platform.event.UserSoftDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class CrewUserSoftDeleteListener {
	private final CrewRepository crewRepository;

	CrewUserSoftDeleteListener(CrewRepository crewRepository) {
		this.crewRepository = crewRepository;
	}

	@EventListener
	void handle(UserSoftDeletedEvent event) {
		crewRepository.deactivateUserCrewState(event.userId());
	}
}
