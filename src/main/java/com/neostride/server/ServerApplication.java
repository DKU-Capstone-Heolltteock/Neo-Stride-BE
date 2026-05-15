package com.neostride.server;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {

	public static final String DEFAULT_TIME_ZONE = "Asia/Seoul";

	static {
		System.setProperty("user.timezone", DEFAULT_TIME_ZONE);
		TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TIME_ZONE));
	}

	public static void main(String[] args) {
		SpringApplication.run(ServerApplication.class, args);
	}

}
