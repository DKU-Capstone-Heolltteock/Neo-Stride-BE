ALTER TABLE community_users
    DROP INDEX uq_community_users_community_profile_name;

ALTER TABLE users
    DROP INDEX uq_users_community_profile_name,
    DROP INDEX uq_users_name,
    DROP INDEX uq_users_email,
    ADD CONSTRAINT email UNIQUE (email);
