package com.neostride.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReportAssignmentRequest(
		@JsonProperty("assigned_operator_account_id")
		Long assignedOperatorAccountId,
		@JsonProperty("reason")
		String reason
) {}
