package com.neostride.server.crew.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewChatMessageRequest(
		@JsonProperty("message_text") @JsonAlias({"messageText", "text"}) String messageText
) {}
