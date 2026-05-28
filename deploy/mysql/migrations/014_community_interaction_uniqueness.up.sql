-- Remove duplicate one-state interactions before adding uniqueness constraints.
DELETE ci
FROM community_interactions ci
JOIN (
    SELECT MIN(interaction_id) AS keep_id, user_id, content_id, interaction_type
    FROM community_interactions
    WHERE interaction_type IN ('LIKE', 'BOOKMARK')
    GROUP BY user_id, content_id, interaction_type
    HAVING COUNT(*) > 1
) duplicate_actions
  ON duplicate_actions.user_id = ci.user_id
 AND duplicate_actions.content_id = ci.content_id
 AND duplicate_actions.interaction_type = ci.interaction_type
WHERE ci.interaction_id <> duplicate_actions.keep_id;

DELETE ci
FROM community_interactions ci
JOIN (
    SELECT MIN(interaction_id) AS keep_id, content_id, tagged_user_id
    FROM community_interactions
    WHERE interaction_type = 'TAG' AND tagged_user_id IS NOT NULL
    GROUP BY content_id, tagged_user_id
    HAVING COUNT(*) > 1
) duplicate_tags
  ON duplicate_tags.content_id = ci.content_id
 AND duplicate_tags.tagged_user_id = ci.tagged_user_id
WHERE ci.interaction_type = 'TAG'
  AND ci.interaction_id <> duplicate_tags.keep_id;

ALTER TABLE community_interactions
    ADD COLUMN action_user_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN interaction_type IN ('LIKE', 'BOOKMARK') THEN user_id ELSE NULL END) VIRTUAL,
    ADD COLUMN action_tagged_user_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN interaction_type = 'TAG' THEN tagged_user_id ELSE NULL END) VIRTUAL;

CREATE UNIQUE INDEX uq_ci_action_user
    ON community_interactions (action_user_id, content_id, interaction_type);

CREATE UNIQUE INDEX uq_ci_tagged_user
    ON community_interactions (action_tagged_user_id, content_id, interaction_type);
