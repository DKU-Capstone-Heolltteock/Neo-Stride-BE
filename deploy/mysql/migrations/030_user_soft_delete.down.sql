ALTER TABLE users
    DROP FOREIGN KEY fk_users_deleted_by_operator,
    DROP INDEX idx_users_deleted_by_operator,
    DROP INDEX idx_users_deleted_at,
    DROP COLUMN deleted_by_operator_id,
    DROP COLUMN deleted_reason,
    DROP COLUMN deleted_at;
