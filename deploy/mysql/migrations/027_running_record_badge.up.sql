ALTER TABLE running_records
    ADD COLUMN badge ENUM('NONE','BRONZE','SILVER','GOLD','PLATINUM','DIAMOND','MASTER','CHALLENGER') NOT NULL DEFAULT 'NONE' AFTER route_detail,
    ADD KEY idx_rr_user_badge_distance (user_id, badge, total_distance DESC, created_at DESC, run_record_id DESC);

UPDATE running_records rr
JOIN (
    SELECT run_record_id, badge
    FROM (
        SELECT
            rr.run_record_id,
            COALESCE(cu.badge, 'NONE') AS badge,
            ROW_NUMBER() OVER (
                PARTITION BY rr.user_id
                ORDER BY rr.total_distance DESC, rr.created_at DESC, rr.run_record_id DESC
            ) AS row_num
        FROM running_records rr
        JOIN community_users cu ON cu.user_id = rr.user_id
        WHERE COALESCE(cu.badge, 'NONE') <> 'NONE'
    ) ranked
    WHERE row_num = 1
) selected ON selected.run_record_id = rr.run_record_id
SET rr.badge = selected.badge;
