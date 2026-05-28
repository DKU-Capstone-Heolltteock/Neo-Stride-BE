CREATE FULLTEXT INDEX ft_cc_content_search
	ON community_contents (title, body_text, content_text);

CREATE FULLTEXT INDEX ft_users_search
	ON users (name, community_profile_name, email);

CREATE FULLTEXT INDEX ft_cu_search
	ON community_users (community_profile_name, status_message);
