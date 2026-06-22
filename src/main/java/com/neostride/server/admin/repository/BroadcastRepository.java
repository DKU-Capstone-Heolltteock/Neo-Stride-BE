package com.neostride.server.admin.repository;

import com.neostride.server.admin.dto.BroadcastResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class BroadcastRepository {
	private final JdbcTemplate jdbcTemplate;

	public BroadcastRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public BroadcastResponse insert(
			long senderOperatorAccountId,
			String title,
			String message,
			String targetType,
			Long targetUserId,
			int recipientCount,
			String status,
			String discordStatus,
			String discordError,
			String reason
	) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var ps = connection.prepareStatement("""
					INSERT INTO operator_broadcasts (
					    sender_operator_account_id, title, message, target_type, target_user_id,
					    recipient_count, status, discord_status, discord_error, reason
					)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", java.sql.Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, senderOperatorAccountId);
			ps.setString(2, title);
			ps.setString(3, message);
			ps.setString(4, targetType);
			if (targetUserId == null) {
				ps.setObject(5, null);
			} else {
				ps.setLong(5, targetUserId);
			}
			ps.setInt(6, recipientCount);
			ps.setString(7, status);
			ps.setString(8, discordStatus);
			ps.setString(9, discordError);
			ps.setString(10, reason);
			return ps;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("방송 ID를 생성하지 못했습니다.");
		}
		return find(key.longValue()).orElseThrow(() -> new IllegalStateException("방송을 저장하지 못했습니다."));
	}

	public List<BroadcastResponse> list(int limit) {
		return jdbcTemplate.query("""
				SELECT broadcast_id, sender_operator_account_id, title, message, target_type, target_user_id,
				       recipient_count, status, discord_status, discord_error, reason, created_at
				FROM operator_broadcasts
				ORDER BY created_at DESC, broadcast_id DESC
				LIMIT ?
				""", this::mapBroadcast, Math.min(Math.max(limit, 1), 200));
	}

	public Optional<BroadcastResponse> find(long broadcastId) {
		List<BroadcastResponse> rows = jdbcTemplate.query("""
				SELECT broadcast_id, sender_operator_account_id, title, message, target_type, target_user_id,
				       recipient_count, status, discord_status, discord_error, reason, created_at
				FROM operator_broadcasts
				WHERE broadcast_id = ?
				""", this::mapBroadcast, broadcastId);
		return rows.stream().findFirst();
	}

	private BroadcastResponse mapBroadcast(ResultSet rs, int rowNum) throws SQLException {
		return new BroadcastResponse(
				rs.getLong("broadcast_id"),
				nullableLong(rs, "sender_operator_account_id"),
				rs.getString("title"),
				rs.getString("message"),
				rs.getString("target_type"),
				nullableLong(rs, "target_user_id"),
				rs.getInt("recipient_count"),
				rs.getString("status"),
				rs.getString("discord_status"),
				rs.getString("discord_error"),
				rs.getString("reason"),
				toLocalDateTime(rs, "created_at")
		);
	}

	private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
		long value = rs.getLong(columnName);
		return rs.wasNull() ? null : value;
	}

	private LocalDateTime toLocalDateTime(ResultSet rs, String columnName) throws SQLException {
		var timestamp = rs.getTimestamp(columnName);
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}
}
