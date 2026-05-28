-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '018_stats_count_drift' AS check_name, COUNT(*) AS failures
FROM (
    SELECT cc.content_id,
           COALESCE(SUM(ci.interaction_type = 'TAG'), 0) AS tagged_count,
           COALESCE(SUM(ci.interaction_type = 'LIKE'), 0) AS like_count,
           COALESCE(SUM(ci.interaction_type = 'COMMENT'), 0) AS comment_count,
           COALESCE(SUM(ci.interaction_type = 'BOOKMARK'), 0) AS bookmark_count
    FROM community_contents cc
    LEFT JOIN community_interactions ci ON ci.content_id = cc.content_id
    GROUP BY cc.content_id
) expected
JOIN community_content_stats stats ON stats.content_id = expected.content_id
WHERE stats.tagged_count <> expected.tagged_count
   OR stats.like_count <> expected.like_count
   OR stats.comment_count <> expected.comment_count
   OR stats.bookmark_count <> expected.bookmark_count
HAVING failures > 0;
