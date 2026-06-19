ALTER TABLE users DROP INDEX ft_users_search;

CREATE FULLTEXT INDEX ft_users_search
	ON users (name, community_profile_name, email);
