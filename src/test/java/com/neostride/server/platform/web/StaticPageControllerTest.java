package com.neostride.server.platform.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticPageControllerTest {

	private final StaticPageController controller = new StaticPageController();

	@Test
	void onboardingWithoutTrailingSlash_redirectsToCanonicalSlashPath() {
		assertThat(controller.onboardingWithoutTrailingSlash()).isEqualTo("redirect:/onboarding/");
	}

	@Test
	void onboardingWithTrailingSlash_forwardsToStaticIndex() {
		assertThat(controller.onboarding()).isEqualTo("forward:/onboarding/index.html");
	}
}
