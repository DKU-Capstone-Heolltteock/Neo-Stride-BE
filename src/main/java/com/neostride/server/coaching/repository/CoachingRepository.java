package com.neostride.server.coaching.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CoachingRepository {

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<PlanDayRow> planDayRowMapper = (rs, rowNum) -> new PlanDayRow(
			rs.getLong("plan_id"),
			rs.getLong("user_id"),
			rs.getLong("goal_id"),
			rs.getDate("plan_date").toLocalDate(),
			rs.getBigDecimal("target_distance"),
			rs.getBigDecimal("target_pace"),
			rs.getBoolean("is_completed"),
			rs.getString("feedback"),
			rs.getTimestamp("updated_at").toLocalDateTime()
	);

	public CoachingRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void deactivateActiveGoals(long userId) {
		jdbcTemplate.update("""
				UPDATE goals
				SET is_active = FALSE
				WHERE user_id = ? AND is_active = TRUE
				""", userId);
	}

	public long insertGoal(GoalInsertCommand command) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO goals
						(user_id, duration_weeks, running_day, target_distance, target_pace, is_active, is_achieved)
					VALUES (?, ?, ?, ?, ?, TRUE, FALSE)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, command.userId());
			ps.setInt(2, command.durationWeeks());
			ps.setInt(3, command.runningDay());
			ps.setBigDecimal(4, command.targetDistance());
			ps.setBigDecimal(5, command.targetPace());
			return ps;
		}, keyHolder);
		Number generatedId = keyHolder.getKey();
		if (generatedId == null) {
			throw new IllegalStateException("목표 ID를 생성하지 못했습니다.");
		}
		return generatedId.longValue();
	}

	public void insertPlanDays(long userId, long goalId, List<PlanDayInsertCommand> commands) {
		if (commands.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate("""
				INSERT INTO plans
					(user_id, goal_id, plan_date, target_distance, target_pace, is_completed)
				VALUES (?, ?, ?, ?, ?, FALSE)
				""", commands, commands.size(), (ps, command) -> {
			ps.setLong(1, userId);
			ps.setLong(2, goalId);
			ps.setObject(3, command.planDate());
			ps.setBigDecimal(4, command.targetDistance());
			ps.setBigDecimal(5, command.targetPace());
		});
	}

	public GoalRow findActiveGoalByUserId(long userId) {
		List<GoalRow> rows = jdbcTemplate.query("""
				SELECT
					g.goal_id, g.user_id, g.duration_weeks, g.running_day, g.target_distance, g.target_pace,
					g.created_at, g.is_active, g.is_achieved,
					MIN(p.plan_date) AS start_date,
					MAX(p.plan_date) AS end_date
				FROM goals g
				LEFT JOIN plans p ON p.goal_id = g.goal_id
				WHERE g.user_id = ? AND g.is_active = TRUE
				GROUP BY g.goal_id, g.user_id, g.duration_weeks, g.running_day, g.target_distance, g.target_pace,
					g.created_at, g.is_active, g.is_achieved
				ORDER BY g.created_at DESC, g.goal_id DESC
				LIMIT 1
				""", goalRowMapper(), userId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	public GoalRow findGoalById(long goalId) {
		List<GoalRow> rows = jdbcTemplate.query("""
				SELECT
					g.goal_id, g.user_id, g.duration_weeks, g.running_day, g.target_distance, g.target_pace,
					g.created_at, g.is_active, g.is_achieved,
					MIN(p.plan_date) AS start_date,
					MAX(p.plan_date) AS end_date
				FROM goals g
				LEFT JOIN plans p ON p.goal_id = g.goal_id
				WHERE g.goal_id = ?
				GROUP BY g.goal_id, g.user_id, g.duration_weeks, g.running_day, g.target_distance, g.target_pace,
					g.created_at, g.is_active, g.is_achieved
				""", goalRowMapper(), goalId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	public GoalRow findGoalByIdForUser(long goalId, long userId) {
		List<GoalRow> rows = jdbcTemplate.query("""
				SELECT
					g.goal_id, g.user_id, g.duration_weeks, g.running_day, g.target_distance, g.target_pace,
					g.created_at, g.is_active, g.is_achieved,
					MIN(p.plan_date) AS start_date,
					MAX(p.plan_date) AS end_date
				FROM goals g
				LEFT JOIN plans p ON p.goal_id = g.goal_id
				WHERE g.goal_id = ? AND g.user_id = ?
				GROUP BY g.goal_id, g.user_id, g.duration_weeks, g.running_day, g.target_distance, g.target_pace,
					g.created_at, g.is_active, g.is_achieved
				""", goalRowMapper(), goalId, userId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	public List<PlanDayRow> findPlanDaysByGoalId(long goalId) {
		return jdbcTemplate.query("""
				SELECT plan_id, user_id, goal_id, plan_date, target_distance, target_pace, is_completed, feedback, updated_at
				FROM plans
				WHERE goal_id = ?
				ORDER BY plan_date ASC, plan_id ASC
				""", planDayRowMapper, goalId);
	}

	public PlanDayRow findTodayPlanByUserId(long userId, LocalDate today) {
		List<PlanDayRow> rows = jdbcTemplate.query("""
				SELECT p.plan_id, p.user_id, p.goal_id, p.plan_date, p.target_distance, p.target_pace, p.is_completed, p.feedback, p.updated_at
				FROM plans p
				JOIN goals g ON g.goal_id = p.goal_id
				WHERE p.user_id = ? AND p.plan_date = ? AND g.is_active = TRUE
				ORDER BY p.plan_id DESC
				LIMIT 1
				""", planDayRowMapper, userId, today);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	public PlanDayRow findPlanDayByIdForUser(long planDayId, long userId) {
		List<PlanDayRow> rows = jdbcTemplate.query("""
				SELECT plan_id, user_id, goal_id, plan_date, target_distance, target_pace, is_completed, feedback, updated_at
				FROM plans
				WHERE plan_id = ? AND user_id = ?
				""", planDayRowMapper, planDayId, userId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	public List<PlanPerformanceRow> findRecentPlanPerformances(long userId, long goalId, int limit) {
		return jdbcTemplate.query("""
				SELECT p.plan_id, p.target_distance, p.target_pace, rr.total_distance, rr.pace
				FROM plans p
				JOIN running_records rr ON rr.plan_id = p.plan_id
				WHERE p.user_id = ? AND p.goal_id = ?
				ORDER BY rr.created_at DESC, rr.run_record_id DESC
				LIMIT ?
				""", (rs, rowNum) -> new PlanPerformanceRow(
				rs.getLong("plan_id"),
				rs.getBigDecimal("target_distance"),
				rs.getBigDecimal("target_pace"),
				rs.getBigDecimal("total_distance"),
				rs.getBigDecimal("pace")
		), userId, goalId, limit);
	}

	public int countPlanDaysThrough(long goalId, LocalDate throughDate) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM plans
				WHERE goal_id = ? AND plan_date <= ?
				""", Integer.class, goalId, throughDate);
		return count == null ? 0 : count;
	}

	public int countCompletedPlanDaysThrough(long goalId, LocalDate throughDate) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM plans
				WHERE goal_id = ? AND plan_date <= ? AND is_completed = TRUE
				""", Integer.class, goalId, throughDate);
		return count == null ? 0 : count;
	}

	public int adjustFuturePlanTargets(long userId, long goalId, LocalDate afterDate, BigDecimal distanceFactor, BigDecimal paceFactor) {
		return jdbcTemplate.update("""
				UPDATE plans
				SET target_distance = GREATEST(0.10, ROUND(target_distance * ?, 2)),
					target_pace = GREATEST(3.00, ROUND(target_pace * ?, 2))
				WHERE user_id = ? AND goal_id = ? AND plan_date > ? AND is_completed = FALSE
				""", distanceFactor, paceFactor, userId, goalId, afterDate);
	}

	public boolean updateFeedbackForUser(long userId, long planDayId, String feedback) {
		int updated = jdbcTemplate.update("""
				UPDATE plans
				SET is_completed = TRUE, feedback = ?, updated_at = CURRENT_TIMESTAMP
				WHERE plan_id = ? AND user_id = ?
				""", feedback, planDayId, userId);
		return updated > 0;
	}

	public boolean deleteGoalForUser(long userId, long goalId) {
		int updated = jdbcTemplate.update("""
				UPDATE goals
				SET is_active = FALSE
				WHERE goal_id = ? AND user_id = ?
				""", goalId, userId);
		return updated > 0;
	}

	public boolean updateGoalStatusForUser(long userId, long goalId, boolean active, boolean achieved) {
		int updated = jdbcTemplate.update("""
				UPDATE goals
				SET is_active = ?, is_achieved = ?
				WHERE goal_id = ? AND user_id = ?
				""", active, achieved, goalId, userId);
		return updated > 0;
	}

	private RowMapper<GoalRow> goalRowMapper() {
		return (rs, rowNum) -> new GoalRow(
				rs.getLong("goal_id"),
				rs.getLong("user_id"),
				rs.getInt("duration_weeks"),
				rs.getInt("running_day"),
				rs.getBigDecimal("target_distance"),
				rs.getBigDecimal("target_pace"),
				rs.getTimestamp("created_at").toLocalDateTime(),
				rs.getBoolean("is_active"),
				rs.getBoolean("is_achieved"),
				rs.getDate("start_date") == null ? null : rs.getDate("start_date").toLocalDate(),
				rs.getDate("end_date") == null ? null : rs.getDate("end_date").toLocalDate(),
				List.of()
		);
	}
}
