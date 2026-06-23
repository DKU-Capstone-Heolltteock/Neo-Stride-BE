CREATE FULLTEXT INDEX ft_cc_feed_text_search
	ON community_contents (title, body_text);
