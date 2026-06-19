package com.neostride.server.platform.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaticPageController {
	@GetMapping({"/onboarding", "/onboarding/"})
	public String onboarding() {
		return "forward:/onboarding/index.html";
	}
}
