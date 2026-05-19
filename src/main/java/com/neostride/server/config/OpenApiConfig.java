package com.neostride.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_AUTH = "bearerAuth";

	private final String baseUrl;

	public OpenApiConfig(@Value("${app.base-url:http://localhost:8080}") String baseUrl) {
		this.baseUrl = baseUrl;
	}

	@Bean
	public OpenAPI neoStrideOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Neo-Stride API")
						.description("Neo-Stride Spring Boot backend API documentation")
						.version("v1"))
				.servers(List.of(new Server()
						.url(baseUrl)
						.description("Configured API server")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
				.components(new Components()
						.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
								.name(BEARER_AUTH)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}
}
