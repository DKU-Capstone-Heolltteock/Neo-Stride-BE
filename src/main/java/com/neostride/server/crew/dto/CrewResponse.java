package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewResponse(
		@JsonProperty("crew_id") Long crewId,
		@JsonProperty("owner_user_id") Long ownerUserId,
		String name,
		String description,
		String visibility,
		@JsonProperty("join_policy") String joinPolicy,
		String region,
		@JsonProperty("profile_image_url") String profileImageUrl,
		@JsonProperty("member_count") Integer memberCount,
		@JsonProperty("viewer_role") String viewerRole,
		@JsonProperty("viewer_status") String viewerStatus,
		@JsonProperty("created_at") String createdAt,
		@JsonProperty("updated_at") String updatedAt
) {}
