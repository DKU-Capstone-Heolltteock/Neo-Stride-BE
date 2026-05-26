DROP INDEX idx_rel_user2_status_user1 ON relationships;
DROP INDEX idx_rel_user1_status_user2 ON relationships;
DROP INDEX idx_ci_tagged_type_content ON community_interactions;
DROP INDEX idx_ci_user_type_content ON community_interactions;
DROP INDEX idx_ci_content_type ON community_interactions;
DROP INDEX idx_cc_author_type_created ON community_contents;
DROP INDEX idx_cc_feed_list ON community_contents;
