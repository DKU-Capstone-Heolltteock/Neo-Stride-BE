package com.neostride.server.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
	private final String allowedOrigins;
	private final String[] allowedMethods;

	public CorsConfig(
			@Value("${neostride.cors.allowed-origins:}") String allowedOrigins,
			@Value("${neostride.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}") String allowedMethods
	) {
		this.allowedOrigins = allowedOrigins == null ? "" : allowedOrigins.trim();
		this.allowedMethods = split(allowedMethods);
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (allowedOrigins.isBlank()) {
			return;
		}
		registry.addMapping("/**")
				.allowedOriginPatterns(split(allowedOrigins))
				.allowedMethods(allowedMethods)
				.allowedHeaders("*")
				.allowCredentials(true)
				.maxAge(3600);
	}

	private static String[] split(String value) {
		if (value == null || value.isBlank()) {
			return new String[0];
		}
		return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(item -> !item.isBlank())
				.toArray(String[]::new);
	}
}
