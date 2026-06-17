package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewEventAttendanceRequest(
		@JsonProperty("user_id") @JsonAlias("userId") Long userId,
		@JsonProperty("running_record_id") @JsonAlias("runningRecordId") Long runningRecordId,
		String status
) {}
