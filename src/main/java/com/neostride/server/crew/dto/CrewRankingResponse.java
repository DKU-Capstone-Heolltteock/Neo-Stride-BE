package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CrewRankingResponse(
		@JsonProperty("crew_id") Long crewId,
		String period,
		String from,
		String to,
		List<CrewRankingEntry> entries
) {}
