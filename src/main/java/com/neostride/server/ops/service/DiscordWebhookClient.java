package com.neostride.server.ops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DiscordWebhookClient {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final HttpClient httpClient;
	private final String webhookUrl;
	private final Duration timeout;

	public DiscordWebhookClient(
			@Value("${neostride.ops.discord.webhook-url:${DISCORD_WEBHOOK_URL:}}") String webhookUrl,
			@Value("${neostride.ops.discord.timeout-ms:${DISCORD_WEBHOOK_TIMEOUT_MS:3000}}") long timeoutMillis
	) {
		this.httpClient = HttpClient.newHttpClient();
		this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
		this.timeout = Duration.ofMillis(Math.max(500, timeoutMillis));
	}

	public DiscordWebhookResult send(String content) {
		if (webhookUrl.isBlank()) {
			return new DiscordWebhookResult("SKIPPED", "Discord webhook URL is not configured.");
		}
		try {
			String body = OBJECT_MAPPER.writeValueAsString(Map.of("content", content));
			HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
					.timeout(timeout)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				return new DiscordWebhookResult("SENT", null);
			}
			return new DiscordWebhookResult("FAILED", "Discord webhook returned HTTP " + response.statusCode());
		} catch (Exception exception) {
			return new DiscordWebhookResult("FAILED", exception.getClass().getSimpleName());
		}
	}

	public record DiscordWebhookResult(String status, String error) {
	}
}
