ALTER TABLE users
    DROP INDEX email,
    ADD CONSTRAINT uq_users_email UNIQUE (email),
    ADD CONSTRAINT uq_users_name UNIQUE (name),
    ADD CONSTRAINT uq_users_community_profile_name UNIQUE (community_profile_name);

ALTER TABLE community_users
    ADD CONSTRAINT uq_community_users_community_profile_name UNIQUE (community_profile_name);
