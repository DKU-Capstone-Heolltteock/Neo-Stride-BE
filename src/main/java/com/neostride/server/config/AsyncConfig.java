package com.neostride.server.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

	@Bean(name = "coachingAiExecutor")
	public Executor coachingAiExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("coaching-ai-");
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(50);
		executor.initialize();
		return executor;
	}

	@Bean(name = "imageThumbnailExecutor")
	public Executor imageThumbnailExecutor(
			@Value("${neostride.image.thumbnail.core-pool-size:1}") int corePoolSize,
			@Value("${neostride.image.thumbnail.max-pool-size:2}") int maxPoolSize,
			@Value("${neostride.image.thumbnail.queue-capacity:32}") int queueCapacity
	) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("image-thumb-");
		executor.setCorePoolSize(Math.max(1, corePoolSize));
		executor.setMaxPoolSize(Math.max(Math.max(1, corePoolSize), maxPoolSize));
		executor.setQueueCapacity(Math.max(0, queueCapacity));
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.initialize();
		return executor;
	}
}
