package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewChatMessageResponse(
		@JsonProperty("message_id") Long messageId,
		@JsonProperty("crew_id") Long crewId,
		@JsonProperty("instant_crew_id") Long instantCrewId,
		@JsonProperty("sender_user_id") Long senderUserId,
		String nickname,
		@JsonProperty("profile_image_url") String profileImageUrl,
		@JsonProperty("message_type") String messageType,
		@JsonProperty("message_text") String messageText,
		@JsonProperty("created_at") String createdAt
) {}
