package com.neostride.server.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadWebConfig implements WebMvcConfigurer {
	private final Path uploadBaseDir;
	private final String publicPrefix;

	public UploadWebConfig(
			@Value("${neostride.upload.base-dir:./uploads}") String uploadBaseDir,
			@Value("${neostride.upload.public-prefix:/uploads}") String publicPrefix
	) {
		this.uploadBaseDir = Path.of(uploadBaseDir).toAbsolutePath().normalize();
		this.publicPrefix = normalizePublicPrefix(publicPrefix);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler(publicPrefix + "/**")
				.addResourceLocations(uploadBaseDir.toUri().toString());
	}

	private static String normalizePublicPrefix(String publicPrefix) {
		String prefix = publicPrefix == null || publicPrefix.isBlank() ? "/uploads" : publicPrefix.trim();
		if (!prefix.startsWith("/")) {
			prefix = "/" + prefix;
		}
		while (prefix.length() > 1 && prefix.endsWith("/")) {
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		return prefix;
	}
}
