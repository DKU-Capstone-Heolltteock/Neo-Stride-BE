CREATE TABLE IF NOT EXISTS community_content_stats (
	content_id BIGINT NOT NULL PRIMARY KEY,
	tagged_count INT NOT NULL DEFAULT 0,
	like_count INT NOT NULL DEFAULT 0,
	comment_count INT NOT NULL DEFAULT 0,
	bookmark_count INT NOT NULL DEFAULT 0,
	updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	CONSTRAINT fk_ccs_content FOREIGN KEY (content_id)
		REFERENCES community_contents(content_id)
		ON DELETE CASCADE
);

INSERT INTO community_content_stats (content_id, tagged_count, like_count, comment_count, bookmark_count)
SELECT cc.content_id,
	COALESCE(SUM(ci.interaction_type = 'TAG'), 0) AS tagged_count,
	COALESCE(SUM(ci.interaction_type = 'LIKE'), 0) AS like_count,
	COALESCE(SUM(ci.interaction_type = 'COMMENT'), 0) AS comment_count,
	COALESCE(SUM(ci.interaction_type = 'BOOKMARK'), 0) AS bookmark_count
FROM community_contents cc
LEFT JOIN community_interactions ci ON ci.content_id = cc.content_id
GROUP BY cc.content_id
ON DUPLICATE KEY UPDATE
	tagged_count = VALUES(tagged_count),
	like_count = VALUES(like_count),
	comment_count = VALUES(comment_count),
	bookmark_count = VALUES(bookmark_count);

DROP TRIGGER IF EXISTS trg_ci_after_insert_stats;
DROP TRIGGER IF EXISTS trg_ci_after_delete_stats;
DROP TRIGGER IF EXISTS trg_ci_after_update_stats;

DELIMITER //
CREATE TRIGGER trg_ci_after_insert_stats
AFTER INSERT ON community_interactions
FOR EACH ROW
BEGIN
	INSERT INTO community_content_stats (content_id, tagged_count, like_count, comment_count, bookmark_count)
	VALUES (
		NEW.content_id,
		IF(NEW.interaction_type = 'TAG', 1, 0),
		IF(NEW.interaction_type = 'LIKE', 1, 0),
		IF(NEW.interaction_type = 'COMMENT', 1, 0),
		IF(NEW.interaction_type = 'BOOKMARK', 1, 0)
	)
	ON DUPLICATE KEY UPDATE
		tagged_count = tagged_count + IF(NEW.interaction_type = 'TAG', 1, 0),
		like_count = like_count + IF(NEW.interaction_type = 'LIKE', 1, 0),
		comment_count = comment_count + IF(NEW.interaction_type = 'COMMENT', 1, 0),
		bookmark_count = bookmark_count + IF(NEW.interaction_type = 'BOOKMARK', 1, 0);
END//

CREATE TRIGGER trg_ci_after_delete_stats
AFTER DELETE ON community_interactions
FOR EACH ROW
BEGIN
	UPDATE community_content_stats
	SET tagged_count = GREATEST(tagged_count - IF(OLD.interaction_type = 'TAG', 1, 0), 0),
		like_count = GREATEST(like_count - IF(OLD.interaction_type = 'LIKE', 1, 0), 0),
		comment_count = GREATEST(comment_count - IF(OLD.interaction_type = 'COMMENT', 1, 0), 0),
		bookmark_count = GREATEST(bookmark_count - IF(OLD.interaction_type = 'BOOKMARK', 1, 0), 0)
	WHERE content_id = OLD.content_id;
END//

CREATE TRIGGER trg_ci_after_update_stats
AFTER UPDATE ON community_interactions
FOR EACH ROW
BEGIN
	IF NOT (OLD.content_id <=> NEW.content_id AND OLD.interaction_type <=> NEW.interaction_type) THEN
		UPDATE community_content_stats
		SET tagged_count = GREATEST(tagged_count - IF(OLD.interaction_type = 'TAG', 1, 0), 0),
			like_count = GREATEST(like_count - IF(OLD.interaction_type = 'LIKE', 1, 0), 0),
			comment_count = GREATEST(comment_count - IF(OLD.interaction_type = 'COMMENT', 1, 0), 0),
			bookmark_count = GREATEST(bookmark_count - IF(OLD.interaction_type = 'BOOKMARK', 1, 0), 0)
		WHERE content_id = OLD.content_id;

		INSERT INTO community_content_stats (content_id, tagged_count, like_count, comment_count, bookmark_count)
		VALUES (
			NEW.content_id,
			IF(NEW.interaction_type = 'TAG', 1, 0),
			IF(NEW.interaction_type = 'LIKE', 1, 0),
			IF(NEW.interaction_type = 'COMMENT', 1, 0),
			IF(NEW.interaction_type = 'BOOKMARK', 1, 0)
		)
		ON DUPLICATE KEY UPDATE
			tagged_count = tagged_count + IF(NEW.interaction_type = 'TAG', 1, 0),
			like_count = like_count + IF(NEW.interaction_type = 'LIKE', 1, 0),
			comment_count = comment_count + IF(NEW.interaction_type = 'COMMENT', 1, 0),
			bookmark_count = bookmark_count + IF(NEW.interaction_type = 'BOOKMARK', 1, 0);
	END IF;
END//
DELIMITER ;
