DROP INDEX uq_ci_tagged_user ON community_interactions;
DROP INDEX uq_ci_action_user ON community_interactions;

ALTER TABLE community_interactions
    DROP COLUMN action_tagged_user_id,
    DROP COLUMN action_user_id;
