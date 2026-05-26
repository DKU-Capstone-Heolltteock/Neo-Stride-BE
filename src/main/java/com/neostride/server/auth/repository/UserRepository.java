package com.neostride.server.auth.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

	private final JdbcTemplate jdbcTemplate;

	public UserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public boolean existsByEmail(String email) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
		return count != null && count > 0;
	}

	public Optional<UserRow> findByEmail(String email) {
		List<UserRow> users = jdbcTemplate.query("""
				SELECT user_id, email, name, password
				FROM users
				WHERE email = ?
				""", (rs, rowNum) -> new UserRow(
				rs.getLong("user_id"),
				rs.getString("email"),
				rs.getString("name"),
				rs.getString("password")
		), email);
		return users.stream().findFirst();
	}

	public long insertUser(String email, String hashedPassword, String name) {
		return insertUser(email, hashedPassword, name, null);
	}

	public long insertUser(String email, String hashedPassword, String name, String profilePhotoUrl) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO users (email, password, name, community_profile_name, profile_photo)
					VALUES (?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, email);
			ps.setString(2, hashedPassword);
			ps.setString(3, name);
			ps.setString(4, name);
			ps.setString(5, profilePhotoUrl);
			return ps;
		}, keyHolder);

		Number generatedId = keyHolder.getKey();
		if (generatedId == null) {
			throw new IllegalStateException("사용자 ID를 생성하지 못했습니다.");
		}
		return generatedId.longValue();
	}
}
