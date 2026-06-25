ALTER TABLE users
    ADD COLUMN deleted_at DATETIME NULL AFTER suspended_by_operator_id,
    ADD COLUMN deleted_reason VARCHAR(1000) NULL AFTER deleted_at,
    ADD COLUMN deleted_by_operator_id BIGINT NULL AFTER deleted_reason,
    ADD KEY idx_users_deleted_at (deleted_at),
    ADD KEY idx_users_deleted_by_operator (deleted_by_operator_id),
    ADD CONSTRAINT fk_users_deleted_by_operator
        FOREIGN KEY (deleted_by_operator_id) REFERENCES operator_accounts (operator_account_id)
        ON DELETE SET NULL;
