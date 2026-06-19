package com.neostride.server.admin.security;

import io.swagger.v3.oas.models.Paths;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminConsoleOpenApiCustomizer {
	@Bean
	public OpenApiCustomizer excludeAdminConsoleApisFromOpenApi() {
		return openApi -> {
			Paths paths = openApi.getPaths();
			if (paths == null) {
				return;
			}
			paths.entrySet().removeIf(entry -> isConsolePath(entry.getKey()));
		};
	}

	private static boolean isConsolePath(String path) {
		return hasPrefix(path, "/api/admin") || hasPrefix(path, "/api/ops") || hasPrefix(path, "/api/dev");
	}

	private static boolean hasPrefix(String path, String prefix) {
		return path.equals(prefix) || path.startsWith(prefix + "/");
	}
}
