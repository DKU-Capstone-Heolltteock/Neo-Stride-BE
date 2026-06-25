package com.neostride.server.community.service;

import com.neostride.server.platform.event.UserSoftDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CommunityUserSoftDeleteListener {
	private final JdbcTemplate jdbcTemplate;

	public CommunityUserSoftDeleteListener(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@EventListener
	public void handle(UserSoftDeletedEvent event) {
		String deletedName = "deleted-user-" + event.userId();
		jdbcTemplate.update("""
				UPDATE community_users
				SET community_profile_name = ?,
				    profile_photo = NULL,
				    status_message = NULL
				WHERE user_id = ?
				""", deletedName, event.userId());
	}
}
