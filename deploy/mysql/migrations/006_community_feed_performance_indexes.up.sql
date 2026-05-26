CREATE INDEX idx_cc_feed_list
	ON community_contents (content_type, feed_scope, created_at DESC, content_id DESC);

CREATE INDEX idx_cc_author_type_created
	ON community_contents (author_user_id, content_type, created_at DESC, content_id DESC);

CREATE INDEX idx_ci_content_type
	ON community_interactions (content_id, interaction_type);

CREATE INDEX idx_ci_user_type_content
	ON community_interactions (user_id, interaction_type, content_id);

CREATE INDEX idx_ci_tagged_type_content
	ON community_interactions (tagged_user_id, interaction_type, content_id);

CREATE INDEX idx_rel_user1_status_user2
	ON relationships (user1_id, status, user2_id);

CREATE INDEX idx_rel_user2_status_user1
	ON relationships (user2_id, status, user1_id);
