package com.neostride.server;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private final String baseUrl;

	public OpenApiConfig(@Value("${app.base-url:http://localhost:8080}") String baseUrl) {
		this.baseUrl = baseUrl;
	}

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("Neo-Stride API")
				.version("v1")
				.description("Neo-Stride backend API documentation"))
			.servers(List.of(new Server()
				.url(baseUrl)
				.description("Local development server")));
	}
}
