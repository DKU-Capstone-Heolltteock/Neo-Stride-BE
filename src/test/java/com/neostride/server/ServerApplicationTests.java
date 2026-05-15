package com.neostride.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ServerApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void defaultTimeZoneIsAsiaSeoul() {
		assertThat(System.getProperty("user.timezone")).isEqualTo(ServerApplication.DEFAULT_TIME_ZONE);
		assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of(ServerApplication.DEFAULT_TIME_ZONE));
	}

}
