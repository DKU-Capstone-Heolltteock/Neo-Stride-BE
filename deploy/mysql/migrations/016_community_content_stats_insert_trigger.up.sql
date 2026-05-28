INSERT INTO community_content_stats (content_id, tagged_count, like_count, comment_count, bookmark_count)
SELECT cc.content_id,
       COALESCE(SUM(ci.interaction_type = 'TAG'), 0) AS tagged_count,
       COALESCE(SUM(ci.interaction_type = 'LIKE'), 0) AS like_count,
       COALESCE(SUM(ci.interaction_type = 'COMMENT'), 0) AS comment_count,
       COALESCE(SUM(ci.interaction_type = 'BOOKMARK'), 0) AS bookmark_count
FROM community_contents cc
LEFT JOIN community_interactions ci ON ci.content_id = cc.content_id
LEFT JOIN community_content_stats stats ON stats.content_id = cc.content_id
WHERE stats.content_id IS NULL
GROUP BY cc.content_id
ON DUPLICATE KEY UPDATE
    tagged_count = VALUES(tagged_count),
    like_count = VALUES(like_count),
    comment_count = VALUES(comment_count),
    bookmark_count = VALUES(bookmark_count);

DROP TRIGGER IF EXISTS trg_cc_after_insert_stats;

DELIMITER //
CREATE TRIGGER trg_cc_after_insert_stats
AFTER INSERT ON community_contents
FOR EACH ROW
BEGIN
    INSERT INTO community_content_stats (content_id)
    VALUES (NEW.content_id)
    ON DUPLICATE KEY UPDATE updated_at = updated_at;
END//
DELIMITER ;
