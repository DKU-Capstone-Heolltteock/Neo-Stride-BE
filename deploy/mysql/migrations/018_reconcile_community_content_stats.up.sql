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
