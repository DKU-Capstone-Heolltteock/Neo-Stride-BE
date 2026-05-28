CREATE INDEX idx_ci_content_type_created
	ON community_interactions (content_id, interaction_type, created_at, interaction_id);

CREATE INDEX idx_rr_user_created
	ON running_records (user_id, created_at DESC, run_record_id DESC);

CREATE INDEX idx_gps_record_time
	ON gps_traces (run_record_id, recorded_time, trace_id);

CREATE INDEX idx_goals_user_active_created
	ON goals (user_id, is_active, created_at DESC, goal_id DESC);

CREATE INDEX idx_plans_user_goal_date
	ON plans (user_id, goal_id, plan_date, plan_id);

CREATE INDEX idx_plans_goal_date
	ON plans (goal_id, plan_date, plan_id);

CREATE INDEX idx_plans_user_date
	ON plans (user_id, plan_date, plan_id);
