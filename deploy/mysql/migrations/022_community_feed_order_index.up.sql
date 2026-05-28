CREATE INDEX idx_cc_type_created
	ON community_contents (content_type, created_at DESC, content_id DESC);
